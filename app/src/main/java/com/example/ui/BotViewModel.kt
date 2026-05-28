package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TokopayInvoice(
    val refId: String,
    val amount: Int,
    val paymentMethod: String,
    val status: String, // PENDING, PAID
    val payUrl: String,
    val qrUrl: String
)

class BotViewModel(private val repository: BotRepository) : ViewModel() {

    private val _isBotRunning = MutableStateFlow(false)
    val isBotRunning: StateFlow<Boolean> = _isBotRunning.asStateFlow()

    private val _isCheckingToken = MutableStateFlow(false)
    val isCheckingToken: StateFlow<Boolean> = _isCheckingToken.asStateFlow()

    private val _botInfoState = MutableStateFlow<TelegramBotInfo?>(null)
    val botInfoState: StateFlow<TelegramBotInfo?> = _botInfoState.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _playgroundResponse = MutableStateFlow<String?>(null)
    val playgroundResponse: StateFlow<String?> = _playgroundResponse.asStateFlow()

    private val _isPlaygroundLoading = MutableStateFlow(false)
    val isPlaygroundLoading: StateFlow<Boolean> = _isPlaygroundLoading.asStateFlow()

    // Admin Auth State
    private val _isAdminLoggedIn = MutableStateFlow(false)
    val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Tokopay Invoice State
    private val _tokopayInvoiceState = MutableStateFlow<TokopayInvoice?>(null)
    val tokopayInvoiceState: StateFlow<TokopayInvoice?> = _tokopayInvoiceState.asStateFlow()

    private val _isTokopayLoading = MutableStateFlow(false)
    val isTokopayLoading: StateFlow<Boolean> = _isTokopayLoading.asStateFlow()

    val settingsState: StateFlow<BotSettings> = repository.settingsFlow
        .map { it ?: BotSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BotSettings()
        )

    val logsState: StateFlow<List<BotLog>> = repository.logsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var pollingJob: Job? = null
    private var lastUpdateId: Long = 0

    init {
        // Observe persistent isBotRunning status and restore it if needed
        viewModelScope.launch {
            val lastSettings = repository.getSettings()
            if (lastSettings != null) {
                _botInfoState.value = TelegramBotInfo(
                    id = 0,
                    is_bot = true,
                    firstName = lastSettings.botFirstName,
                    username = lastSettings.botUsername.takeIf { it.isNotBlank() }
                )
                if (lastSettings.isBotRunning && lastSettings.telegramToken.isNotBlank()) {
                    startBotPolling()
                }
            }
        }
    }

    fun saveBotSettings(
        token: String,
        geminiKey: String,
        instruction: String
    ) {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(
                telegramToken = token.trim(),
                geminiApiKey = geminiKey.trim(),
                systemInstruction = instruction
            )
            repository.saveSettings(updated)
            repository.addLog("INFO", "Konfigurasi bot disimpan.")
        }
    }

    fun validateAndTestToken(token: String) {
        if (token.isBlank()) {
            _validationError.value = "Token Telegram tidak boleh kosong."
            return
        }
        _validationError.value = null
        _isCheckingToken.value = true

        viewModelScope.launch {
            try {
                repository.addLog("INFO", "Menguji validitas Token Telegram...")
                val result = repository.validateTelegramBot(token.trim())
                _botInfoState.value = result
                _validationError.value = null

                // Save cached info
                val current = settingsState.value
                repository.saveSettings(
                    current.copy(
                        telegramToken = token.trim(),
                        botFirstName = result.firstName,
                        botUsername = result.username ?: ""
                    )
                )
                repository.addLog("SUCCESS", "Token valid! Bot terhubung sebagai @${result.username} (${result.firstName}).")
            } catch (e: Exception) {
                _validationError.value = e.localizedMessage ?: "Gagal memverifikasi token."
                repository.addLog("ERROR", "Uji token gagal: ${e.message}")
            } finally {
                _isCheckingToken.value = false
            }
        }
    }

    fun toggleBotState() {
        if (_isBotRunning.value) {
            stopBotPolling()
        } else {
            startBotPolling()
        }
    }

    private fun startBotPolling() {
        val currentSettings = settingsState.value
        if (currentSettings.telegramToken.isBlank()) {
            viewModelScope.launch {
                repository.addLog("ERROR", "Gagal mengaktifkan bot: Token Telegram belum diisi.")
            }
            return
        }

        _isBotRunning.value = true
        // Persist isRunning state inside DB
        viewModelScope.launch {
            repository.saveSettings(currentSettings.copy(isBotRunning = true))
            repository.addLog("INFO", "Mengaktifkan bot telegram...")
        }

        pollingJob = viewModelScope.launch {
            try {
                // Perform quick pre-validation
                val info = repository.validateTelegramBot(currentSettings.telegramToken)
                _botInfoState.value = info
                repository.addLog("SUCCESS", "Bot @${info.username} aktif dan berjalan.")

                while (_isBotRunning.value) {
                    try {
                        val updates = repository.fetchTelegramUpdates(
                            token = currentSettings.telegramToken,
                            offset = if (lastUpdateId > 0) lastUpdateId else null,
                            timeout = 10
                        )

                        for (update in updates) {
                            lastUpdateId = update.updateId
                            val message = update.message
                            val text = message?.text
                            val chat = message?.chat
                            val from = message?.from

                            if (text != null && chat != null) {
                                val senderName = from?.firstName ?: "User"
                                val chatUsernameString = from?.username?.let { "@$it" } ?: "ID: ${chat.id}"
                                repository.addLog("INCOMING", "Pesan dari \$senderName (\$chatUsernameString): \"$text\"")

                                // Trigger Gemini response asynchronously to keep polling ultra-responsive
                                launch {
                                    handleIncomingMessage(
                                        token = currentSettings.telegramToken,
                                        chatId = chat.id,
                                        messageId = message.messageId,
                                        textMessage = text,
                                        senderName = senderName,
                                        systemInstruction = currentSettings.systemInstruction,
                                        apiKey = currentSettings.geminiApiKey
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val errorMsg = e.localizedMessage ?: e.message ?: "Koneksi Bermasalah"
                        Log.e("BotViewModel", "Polling loop issue: $errorMsg")
                        repository.addLog("WARNING", "Masalah koneksi polling: $errorMsg. Mencoba kembali dalam 5 detik...")
                        delay(5000)
                    }
                    delay(1000) // Politeness delay
                }
            } catch (e: Exception) {
                _isBotRunning.value = false
                repository.saveSettings(currentSettings.copy(isBotRunning = false))
                repository.addLog("ERROR", "Gagal mengaktifkan Bot. Error: ${e.message}")
            }
        }
    }

    private suspend fun handleIncomingMessage(
        token: String,
        chatId: Long,
        messageId: Long,
        textMessage: String,
        senderName: String,
        systemInstruction: String,
        apiKey: String
    ) {
        try {
            repository.addLog("INFO", "Menghubungi Gemini AI...")
            val enrichedPrompt = "Seorang pengguna bernama $senderName berinteraksi dengan Anda di bot Telegram. Dia berkata: \"$textMessage\". Harap balas dengan sopan sesuai instruksi sistem."
            val aiResponse = repository.askGemini(enrichedPrompt, apiKey, systemInstruction)

            repository.addLog("OUTGOING", "Balasan AI Gemini: \"$aiResponse\"")
            
            repository.sendTelegramMessage(
                token = token,
                chatId = chatId,
                text = aiResponse,
                replyToMessageId = messageId
            )
            repository.addLog("SUCCESS", "Balasan berhasil dikirim ke @$senderName")
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: e.message ?: "Unknown Error"
            repository.addLog("ERROR", "Gagal menjawab pesan: $errMsg")
            
            // Gracefully offer a fallback error message on telegram so the user knows what happened
            try {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Maaf, sistem AI sedang mengalami gangguan teknis. Silakan coba kirim pesan lagi nanti.\n*(Detail: $errMsg)*",
                    replyToMessageId = messageId
                )
            } catch (ignored: Exception) {}
        }
    }

    private fun stopBotPolling() {
        _isBotRunning.value = false
        pollingJob?.cancel()
        pollingJob = null
        viewModelScope.launch {
            val current = settingsState.value
            repository.saveSettings(current.copy(isBotRunning = false))
            repository.addLog("INFO", "Bot telegram dinonaktifkan.")
        }
    }

    /**
     * Clear all logs from DB
     */
    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.addLog("INFO", "Log aktivitas dibersihkan.")
        }
    }

    /**
     * Tests Gemini integration inside a sandbox environment directly in-app
     */
    fun testGeminiPlayground(prompt: String) {
        if (prompt.isBlank()) return
        _isPlaygroundLoading.value = true
        _playgroundResponse.value = null

        viewModelScope.launch {
            try {
                val instruction = settingsState.value.systemInstruction
                val key = settingsState.value.geminiApiKey
                repository.addLog("INFO", "Menguji prompt Gemini AI di antarmuka sandbox...")
                val response = repository.askGemini(prompt, key, instruction)
                _playgroundResponse.value = response
                repository.addLog("SUCCESS", "Uji Gemini Berhasil! Tanggapan diterima.")
            } catch (e: Exception) {
                _playgroundResponse.value = "Error: ${e.message}"
                repository.addLog("ERROR", "Uji Gemini gagal: ${e.message}")
            } finally {
                _isPlaygroundLoading.value = false
            }
        }
    }

    /**
     * Admin Log-In Verification Flow
     */
    fun loginAdmin(email: String, password: String): Boolean {
        _loginError.value = null
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        
        if (trimmedEmail == "rizakontol@kontol.my.id" && trimmedPassword == "rizakontol") {
            _isAdminLoggedIn.value = true
            _loginError.value = null
            viewModelScope.launch {
                repository.addLog("SUCCESS", "Admin Login Berhasil: Masuk sebagai rizakontol@kontol.my.id")
            }
            return true
        } else {
            _loginError.value = "Email atau Password salah! Periksa kembali kredensial Anda."
            viewModelScope.launch {
                repository.addLog("ERROR", "Percobaan Admin Login Gagal untuk email: $trimmedEmail")
            }
            return false
        }
    }

    /**
     * Terminate Admin Session Flow
     */
    fun logoutAdmin() {
        _isAdminLoggedIn.value = false
        _loginError.value = null
        viewModelScope.launch {
            repository.addLog("INFO", "Sesi administrator diakhiri (Logout).")
        }
    }

    /**
     * Save Tokopay Gateway configurations (EXCLUSIVE TO ADMIN)
     */
    fun saveTokopaySettings(
        merchantId: String,
        secretKey: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(
                tokopayMerchantId = merchantId.trim(),
                tokopaySecretKey = secretKey.trim(),
                tokopayIsActive = isActive
            )
            repository.saveSettings(updated)
            repository.addLog("SUCCESS", "Konfigurasi Tokopay berhasil disimpan oleh Admin. Status keaktifan: ${if (isActive) "AKTIF" else "NONAKTIF"}")
        }
    }

    /**
     * Request simulated Tokopay invoice/payment link creation
     */
    fun createTokopayInvoice(amount: Int, paymentMethod: String) {
        if (amount <= 0) return
        _isTokopayLoading.value = true
        _tokopayInvoiceState.value = null
        viewModelScope.launch {
            delay(1000) // Simulating network lag
            val refId = "TP-" + System.currentTimeMillis().toString().takeLast(6)
            val invoiceUrl = "https://tokopay.id/bill/$refId"
            
            // Dynamic Indonesia standard QRIS simulation URL
            val qrCodeSimUrl = "https://api.qrserver.com/v1/create-qr-code/?size=350x350&data=00020101021226300010ID.CO.QRIS.WWW02041234567803030005204000053033605405${amount}5802ID5908Tokopay6009Jakarta61051234562070703030006304"
            
            val invoice = TokopayInvoice(
                refId = refId,
                amount = amount,
                paymentMethod = paymentMethod,
                status = "PENDING",
                payUrl = invoiceUrl,
                qrUrl = qrCodeSimUrl
            )
            _tokopayInvoiceState.value = invoice
            _isTokopayLoading.value = false
            repository.addLog("SUCCESS", "Tokopay API: Invoice $refId sebesar Rp $amount berhasil diterbitkan via $paymentMethod.")
        }
    }

    /**
     * Simulate an incoming Webhook notification from Tokopay verifying payment success
     */
    fun simulatePaymentSuccess() {
        val currentInvoice = _tokopayInvoiceState.value ?: return
        _isTokopayLoading.value = true
        viewModelScope.launch {
            delay(1000)
            val updated = currentInvoice.copy(status = "PAID")
            _tokopayInvoiceState.value = updated
            _isTokopayLoading.value = false
            repository.addLog("SUCCESS", "Tokopay Webhook: Pembayaran Invoice ${currentInvoice.refId} sebesar Rp ${currentInvoice.amount} terverifikasi LUNAS (PAID)!")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopBotPolling()
    }
}

class BotViewModelFactory(private val repository: BotRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BotViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BotViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
