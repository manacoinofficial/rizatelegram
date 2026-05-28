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

    private val _isCheckingToken = MutableStateFlow(false)
    val isCheckingToken: StateFlow<Boolean> = _isCheckingToken.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _playgroundResponse = MutableStateFlow<String?>(null)
    val playgroundResponse: StateFlow<String?> = _playgroundResponse.asStateFlow()

    private val _isPlaygroundLoading = MutableStateFlow(false)
    val isPlaygroundLoading: StateFlow<Boolean> = _isPlaygroundLoading.asStateFlow()

    // Admin Auth State
    private val _isAdminLoggedIn = MutableStateFlow(false)
    val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn.asStateFlow()

    // User Auth State
    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

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

    val isBotRunning: StateFlow<Boolean> = settingsState
        .map { it.isBotRunning }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val botInfoState: StateFlow<TelegramBotInfo?> = settingsState
        .map { settings ->
            if (settings.botUsername.isNotBlank()) {
                TelegramBotInfo(
                    id = 0,
                    is_bot = true,
                    firstName = settings.botFirstName,
                    username = settings.botUsername
                )
            } else {
                null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
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
        // Observer in MainActivity will start service on app relaunch
    }

    fun saveBotSettings(
        token: String,
        groqKey: String,
        selectedModel: String,
        instruction: String
    ) {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(
                telegramToken = token.trim(),
                groqApiKey = groqKey.trim(),
                selectedModel = selectedModel.trim(),
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

    fun toggleBotState(context: android.content.Context) {
        val currentSettings = settingsState.value
        if (currentSettings.isBotRunning) {
            BotService.stopService(context)
        } else {
            if (currentSettings.telegramToken.isBlank()) {
                viewModelScope.launch {
                    repository.addLog("ERROR", "Gagal mengaktifkan bot: Token Telegram belum diisi.")
                }
                return
            }
            BotService.startService(context)
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
     * Tests Groq integration inside a sandbox environment directly in-app
     */
    fun testGroqPlayground(prompt: String) {
        if (prompt.isBlank()) return
        _isPlaygroundLoading.value = true
        _playgroundResponse.value = null

        viewModelScope.launch {
            try {
                val instruction = settingsState.value.systemInstruction
                val key = settingsState.value.groqApiKey
                val model = settingsState.value.selectedModel
                repository.addLog("INFO", "Menguji prompt Groq AI ($model) di antarmuka sandbox...")
                val response = repository.askGroq(prompt, key, model, instruction)
                _playgroundResponse.value = response
                repository.addLog("SUCCESS", "Uji Groq Berhasil! Tanggapan diterima.")
            } catch (e: Exception) {
                _playgroundResponse.value = "Error: ${e.message}"
                repository.addLog("ERROR", "Uji Groq gagal: ${e.message}")
            } finally {
                _isPlaygroundLoading.value = false
            }
        }
    }

    fun selectModel(modelName: String) {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(selectedModel = modelName.trim())
            repository.saveSettings(updated)
            repository.addLog("INFO", "Model aktif diubah menjadi: $modelName")
        }
    }

    fun addGroqModel(modelName: String) {
        val trimmed = modelName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = settingsState.value
            val currentList = current.groqModels.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()

            if (currentList.contains(trimmed)) {
                repository.addLog("WARNING", "Model \"$trimmed\" sudah ada didaftar.")
                return@launch
            }

            currentList.add(trimmed)
            val updatedString = currentList.joinToString(",")
            val updated = current.copy(groqModels = updatedString)
            repository.saveSettings(updated)
            repository.addLog("SUCCESS", "Model baru ditambahkan: $trimmed")
        }
    }

    fun deleteGroqModel(modelName: String) {
        val trimmed = modelName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = settingsState.value
            val currentList = current.groqModels.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()

            if (!currentList.contains(trimmed)) {
                repository.addLog("WARNING", "Model \"$trimmed\" tidak ditemukan didaftar.")
                return@launch
            }

            if (currentList.size <= 1) {
                repository.addLog("WARNING", "Gagal menghapus. Minimal harus menyisakan 1 model aktif.")
                return@launch
            }

            currentList.remove(trimmed)
            val updatedString = currentList.joinToString(",")
            val nextSelected = if (current.selectedModel == trimmed) {
                currentList.first()
            } else {
                current.selectedModel
            }

            val updated = current.copy(
                groqModels = updatedString,
                selectedModel = nextSelected
            )
            repository.saveSettings(updated)
            repository.addLog("SUCCESS", "Model dihapus: $trimmed")
        }
    }

    fun clearLoginError() {
        _loginError.value = null
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
            _isUserLoggedIn.value = false // Ensure exclusive
            _loginError.value = null
            viewModelScope.launch {
                repository.addLog("SUCCESS", "Admin Login Berhasil: Masuk sebagai rizakontol@kontol.my.id")
            }
            return true
        } else {
            _loginError.value = "Email atau Password Admin salah! Periksa kembali kredensial Anda."
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
     * User Log-In Verification Flow
     */
    fun loginUser(email: String, password: String): Boolean {
        _loginError.value = null
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        
        if ((trimmedEmail == "userkontol@kontol.my.id" && trimmedPassword == "userkontol") || 
            (trimmedEmail == "user@gmail.com" && trimmedPassword == "user123")) {
            _isUserLoggedIn.value = true
            _isAdminLoggedIn.value = false // Ensure exclusive
            _loginError.value = null
            viewModelScope.launch {
                repository.addLog("SUCCESS", "User Login Berhasil: Masuk sebagai $trimmedEmail")
            }
            return true
        } else {
            _loginError.value = "Email atau Password User salah! Periksa kembali kredensial Anda."
            viewModelScope.launch {
                repository.addLog("ERROR", "Percobaan User Login Gagal untuk email: $trimmedEmail")
            }
            return false
        }
    }

    /**
     * Terminate User Session Flow
     */
    fun logoutUser() {
        _isUserLoggedIn.value = false
        _loginError.value = null
        viewModelScope.launch {
            repository.addLog("INFO", "Sesi User diakhiri (Logout).")
        }
    }

    /**
     * Save Tokopay Gateway configurations (EXCLUSIVE TO ADMIN)
     */
    fun saveTokopaySettings(
        merchantId: String,
        apiKey: String,
        secretKey: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = current.copy(
                tokopayMerchantId = merchantId.trim(),
                tokopayApiKey = apiKey.trim(),
                tokopaySecretKey = secretKey.trim(),
                tokopayIsActive = isActive
            )
            repository.saveSettings(updated)
            repository.addLog("SUCCESS", "Konfigurasi Tokopay berhasil disimpan oleh Admin. Status keaktifan: ${if (isActive) "AKTIF" else "NONAKTIF"}")
        }
    }

    /**
     * Request mock Tokopay invoice/payment link creation
     */
    fun createTokopayInvoice(amount: Int, paymentMethod: String) {
        if (amount <= 0) return
        _isTokopayLoading.value = true
        _tokopayInvoiceState.value = null
        viewModelScope.launch {
            delay(1000) // Mock network lag
            val refId = "TP-" + System.currentTimeMillis().toString().takeLast(6)
            val invoiceUrl = "https://tokopay.id/bill/$refId"
            
            // Dynamic Indonesia standard QRIS mock URL
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
     * Trigger an incoming Webhook notification from Tokopay verifying payment success
     */
    fun triggerPaymentSuccessCallback() {
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
