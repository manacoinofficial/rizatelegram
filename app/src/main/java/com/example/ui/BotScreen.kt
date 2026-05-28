package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BotLog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotScreen(
    viewModel: BotViewModel,
    modifier: Modifier = Modifier
) {
    val isRunning by viewModel.isBotRunning.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingToken.collectAsStateWithLifecycle()
    val botInfo by viewModel.botInfoState.collectAsStateWithLifecycle()
    val validationError by viewModel.validationError.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val logs by viewModel.logsState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dashboard", "Terminal Log", "Sandbox AI", "Panduan Setup")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Bot Logo",
                            tint = CyberPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "AI Telegram Bot",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = CyberTextPrimary
                            )
                            Text(
                                "Integrasi Gemini AI & Telegram",
                                fontSize = 11.sp,
                                color = CyberTextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Status Light Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(CyberSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) CyberPrimary else CyberError)
                        )
                        Text(
                            text = if (isRunning) "ACTIVE" else "OFFLINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isRunning) CyberPrimary else CyberError
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberBackground,
                    titleContentColor = CyberTextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBackground)
                .padding(innerPadding)
        ) {
            // Horizontal Tab indicator for clean single-view switching
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CyberBackground,
                contentColor = CyberPrimary,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = CyberCardBorder) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = CyberPrimary,
                        unselectedContentColor = CyberTextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        isRunning = isRunning,
                        isChecking = isChecking,
                        botInfo = botInfo,
                        validationError = validationError,
                        settingsToken = settings.telegramToken,
                        settingsGeminiKey = settings.geminiApiKey,
                        settingsInstruction = settings.systemInstruction
                    )
                    1 -> LogsTab(
                        logs = logs,
                        onClearLogs = { viewModel.clearLogs() }
                    )
                    2 -> SandboxTab(viewModel = viewModel)
                    3 -> HelpTab()
                }
            }
        }
    }
}

// --- TAB 1: DASHBOARD ---
@Composable
fun DashboardTab(
    viewModel: BotViewModel,
    isRunning: Boolean,
    isChecking: Boolean,
    botInfo: com.example.data.TelegramBotInfo?,
    validationError: String?,
    settingsToken: String,
    settingsGeminiKey: String,
    settingsInstruction: String
) {
    // Local form states synced with database values
    var localToken by remember(settingsToken) { mutableStateOf(settingsToken) }
    var localGeminiKey by remember(settingsGeminiKey) { mutableStateOf(settingsGeminiKey) }
    var localInstruction by remember(settingsInstruction) { mutableStateOf(settingsInstruction) }

    var tokenObscured by remember { mutableStateOf(true) }
    var keyObscured by remember { mutableStateOf(true) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Runner Status Control
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, if (isRunning) CyberPrimary else CyberCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Server Control Bot",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = CyberTextPrimary
                            )
                            Text(
                                if (isRunning) "Bot aktif dan merespon pesan Telegram..." else "Bot tidak aktif. Klik tombol RUN untuk memulai.",
                                fontSize = 12.sp,
                                color = CyberTextSecondary
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleBotState() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) CyberError else CyberPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("run_bot_button")
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "Run toggle"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRunning) "STOP" else "RUN")
                        }
                    }

                    // Connected Info Panel
                    if (botInfo != null) {
                        HorizontalDivider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CyberPrimary.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active Bot",
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    botInfo.firstName,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    if (botInfo.username != null) "@${botInfo.username}" else "No Username",
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Credentials Config Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, CyberCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Konfigurasi Kredensial",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CyberPrimary
                    )

                    // Telegram Token
                    OutlinedTextField(
                        value = localToken,
                        onValueChange = { localToken = it },
                        label = { Text("API Token Bot Telegram") },
                        placeholder = { Text("Masukkan token dari @BotFather") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telegram_token_input"),
                        visualTransformation = if (tokenObscured) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { tokenObscured = !tokenObscured }) {
                                Icon(
                                    imageVector = if (tokenObscured) Icons.Default.Lock else Icons.Default.Refresh,
                                    contentDescription = "Toggle token view"
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberPrimary,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedLabelColor = CyberPrimary,
                            unfocusedLabelColor = CyberTextSecondary,
                            focusedTextColor = CyberTextPrimary,
                            unfocusedTextColor = CyberTextPrimary
                        )
                    )

                    // Gemini Key
                    OutlinedTextField(
                        value = localGeminiKey,
                        onValueChange = { localGeminiKey = it },
                        label = { Text("API Key Gemini AI") },
                        placeholder = { Text("Menggunakan default AI Studio (Secrets)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_key_input"),
                        visualTransformation = if (keyObscured) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { keyObscured = !keyObscured }) {
                                Icon(
                                    imageVector = if (keyObscured) Icons.Default.Lock else Icons.Default.Refresh,
                                    contentDescription = "Toggle key view"
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberSecondary,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedLabelColor = CyberSecondary,
                            unfocusedLabelColor = CyberTextSecondary,
                            focusedTextColor = CyberTextPrimary,
                            unfocusedTextColor = CyberTextPrimary
                        )
                    )

                    // Custom System instruction/Prompt styling
                    OutlinedTextField(
                        value = localInstruction,
                        onValueChange = { localInstruction = it },
                        label = { Text("System Persona & Aturan AI (System Prompt)") },
                        placeholder = { Text("Ketik instruksi watak AI disini...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberPrimary,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedLabelColor = CyberPrimary,
                            unfocusedLabelColor = CyberTextSecondary,
                            focusedTextColor = CyberTextPrimary,
                            unfocusedTextColor = CyberTextPrimary
                        )
                    )

                    // Buttons Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Quick Check Bot Token Validation
                        OutlinedButton(
                            onClick = { viewModel.validateAndTestToken(localToken) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberSecondary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberSecondary)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberSecondary, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Info, contentDescription = "Check Connection", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uji Token")
                            }
                        }

                        // Save Configuration
                        Button(
                            onClick = {
                                viewModel.saveBotSettings(localToken, localGeminiKey, localInstruction)
                                Toast.makeText(context, "Sistem tersimpan & diperbarui!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary, contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Save settings", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan")
                        }
                    }

                    // Display token Validation failure if exists
                    if (validationError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberError.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .border(1.dp, CyberError, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Error Logo", tint = CyberError, modifier = Modifier.size(20.dp))
                                Text(validationError, fontSize = 12.sp, color = CyberError, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 2: TERMINAL LOGS ---
@Composable
fun LogsTab(
    logs: List<BotLog>,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Automatically scroll to top/bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.List, contentDescription = "Console", tint = CyberPrimary, modifier = Modifier.size(20.dp))
            Text(
                "Terminal Realtime Bot Logs",
                fontWeight = FontWeight.Bold,
                color = CyberTextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = onClearLogs,
                colors = ButtonDefaults.textButtonColors(contentColor = CyberError)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bersihkan Log")
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, CyberCardBorder, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = CyberSurfaceVariant
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty Log",
                            tint = CyberTextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Belum ada log aktivitas.",
                            fontWeight = FontWeight.Bold,
                            color = CyberTextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            "Mulai polling bot di tab Dashboard, atau kirim pesan ke bot Telegram Anda untuk melihat aktivitas live komunikasi AI.",
                            color = CyberTextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { log ->
                        LogLineItem(
                            log = log,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(log.message))
                                Toast.makeText(context, "Log disalin!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogLineItem(log: BotLog, onCopy: () -> Unit) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = formatter.format(Date(log.timestamp))

    val (tagColor, tagText) = when (log.level) {
        "SUCCESS" -> CyberPrimary to " OK "
        "ERROR" -> CyberError to "FAIL"
        "WARNING" -> CyberAccent to "WARN"
        "INCOMING" -> Color(0xFFA855F7) to "RECV"
        "OUTGOING" -> Color(0xFF06B6D4) to "SEND"
        else -> CyberTextSecondary to "INFO"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Log Timestamp
        Text(
            text = "[$timeStr]",
            color = CyberTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )

        // Level Tag
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(tagColor.copy(alpha = 0.15f))
                .border(0.5.dp, tagColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = tagText,
                color = tagColor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        // Message text
        Text(
            text = log.message,
            color = if (log.level == "ERROR") CyberError else CyberTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// --- TAB 3: PLAYGROUND TESTING ---
@Composable
fun SandboxTab(viewModel: BotViewModel) {
    var promptInput by remember { mutableStateOf("") }
    val isPlaygroundLoading by viewModel.isPlaygroundLoading.collectAsStateWithLifecycle()
    val playgroundResponse by viewModel.playgroundResponse.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, CyberCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Playground", tint = CyberSecondary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                "AI Gemini Sandbox Playground",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CyberTextPrimary
                            )
                            Text(
                                "Uji watak dan watak respon bot Anda lewat sandbox internal ini.",
                                fontSize = 11.sp,
                                color = CyberTextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        label = { Text("Kirim Pesan Simulasi") },
                        placeholder = { Text("Tulis pesan simulasi seolah dikirim oleh pengguna Telegram...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp)
                            .testTag("sandbox_prompt_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberSecondary,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = CyberTextPrimary,
                            unfocusedTextColor = CyberTextPrimary
                        )
                    )

                    Button(
                        onClick = { viewModel.testGeminiPlayground(promptInput) },
                        enabled = !isPlaygroundLoading && promptInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sandbox_submit_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary, contentColor = Color.White)
                    ) {
                        if (isPlaygroundLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Compute Response", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uji Respon Gemini AI")
                        }
                    }
                }
            }
        }

        if (playgroundResponse != null || isPlaygroundLoading) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
                    border = BorderStroke(1.dp, CyberSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Hasil Output Model (gemini-3.5-flash):",
                            fontWeight = FontWeight.Bold,
                            color = CyberSecondary,
                            fontSize = 13.sp
                        )

                        if (isPlaygroundLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberSecondary, strokeWidth = 2.dp)
                                Text("Sedang memproses prompt...", color = CyberTextSecondary, fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                playgroundResponse ?: "",
                                fontSize = 14.sp,
                                color = CyberTextPrimary,
                                lineHeight = 20.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.testTag("sandbox_result_text")
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 4: SETUP GUIDE ---
@Composable
fun HelpTab() {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Panduan Pembuatan & Integrasi Bot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = CyberPrimary
                )
                Text(
                    "Ikuti langkah-langkah terperinci di bawah ini untuk mengaktifkan AI Bot Anda.",
                    fontSize = 12.sp,
                    color = CyberTextSecondary
                )
            }
        }

        item {
            StepCard(
                stepNumber = "1",
                title = "Buat Bot lewat @BotFather",
                description = "Buka aplikasi Telegram Anda, ketik pencarian @BotFather. Tekan tombol /start. Kirim pesan /newbot untuk membuat telegram bot baru.",
                hintText = "Pastikan mencari akun asli dengan lencana verifikasi biru."
            )
        }

        item {
            StepCard(
                stepNumber = "2",
                title = "Gelar Nama & Username Bot",
                description = "Ikuti instruksi BotFather: Tentukan nama bot (contoh: Gemini Chat AI) dan ketik nama pengguna (username) bot Anda yang unik, diakhiri dengan kata '_bot' (contoh: asisten_hebat_game_bot).",
                hintText = "Nama pengguna tidak boleh mengandung karakter aneh selain underscore."
            )
        }

        item {
            StepCard(
                stepNumber = "3",
                title = "Dapatkan Token API Rahasia",
                description = "Setelah selesai, BotFather akan mengirimkan pesan balasan berisi HTTP API Token berupa karakter unik (contoh: 123456789:AAEhiJK...).\nSalin/Copy token itu!",
                codeToCopy = "123456789:AAE-CopyTokenAndaDisini",
                onCopy = {
                    clipboardManager.setText(AnnotatedString("Kopi token API dari telegram Anda!"))
                    Toast.makeText(context, "Petunjuk token disalin!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            StepCard(
                stepNumber = "4",
                title = "Tempel & Jalankan Bot!",
                description = "Keluar dari Telegram, buka aplikasi ini. Klik tab Dashboard, tempel Token Anda di input form, klik 'Simpan', lalu geser/klik tombol 'RUN BOT' di card Server Control.",
                hintText = "Apabila server menyala berwarna hijau (ACTIVE), bot siap melayani."
            )
        }

        item {
            StepCard(
                stepNumber = "5",
                title = "Mulai Chat di Telegram!",
                description = "Segera buka bot Telegram buatan Anda dengan cara mengetik nama penggunanya di kotak pencarian Telegram, masuk ke chat room, klik /start, kirim teks pesan secara bebas. Bot Anda akan merespon menggunakan kecerdasan buatan Gemini AI secara otomatis seketika!",
                hintText = "Anda juga bisa memantau lalu-lintas data teks pada tab 'Terminal Log' realtime!"
            )
        }
    }
}

@Composable
fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    hintText: String? = null,
    codeToCopy: String? = null,
    onCopy: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = BorderStroke(1.dp, CyberCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CyberPrimary)
                ) {
                    Text(
                        stepNumber,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary,
                    fontSize = 14.sp
                )
            }

            Text(
                description,
                fontSize = 12.sp,
                color = CyberTextSecondary,
                lineHeight = 18.sp
            )

            if (codeToCopy != null && onCopy != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurfaceVariant, RoundedCornerShape(6.dp))
                        .border(0.5.dp, CyberCardBorder, RoundedCornerShape(6.dp))
                        .clickable { onCopy() }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        codeToCopy,
                        fontSize = 11.sp,
                        color = CyberPrimary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Copy",
                        tint = CyberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (hintText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSecondary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Tips",
                        tint = CyberSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        hintText,
                        fontSize = 11.sp,
                        color = CyberSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
