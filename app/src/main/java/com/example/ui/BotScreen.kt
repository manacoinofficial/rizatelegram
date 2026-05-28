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

@Composable
fun QrisQrCodeVisual(amount: Int) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(2.dp, CyberPrimary, RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val sizePx = size.width
            val cellSize = sizePx / 15f
            
            fun drawFinderPattern(x: Float, y: Float) {
                drawRect(
                    color = Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(cellSize * 4, cellSize * 4)
                )
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(x + cellSize, y + cellSize),
                    size = androidx.compose.ui.geometry.Size(cellSize * 2, cellSize * 2)
                )
                drawRect(
                    color = Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(x + cellSize * 1.25f, y + cellSize * 1.25f),
                    size = androidx.compose.ui.geometry.Size(cellSize * 1.5f, cellSize * 1.5f)
                )
            }
            
            drawFinderPattern(0f, 0f)
            drawFinderPattern(sizePx - cellSize * 4, 0f)
            drawFinderPattern(0f, sizePx - cellSize * 4)
            
            val random = java.util.Random(amount.toLong() + 12345)
            for (col in 0 until 15) {
                for (row in 0 until 15) {
                    val isFinderZone = (row < 4 && col < 4) || (row < 4 && col >= 11) || (row >= 11 && col < 4)
                    if (!isFinderZone) {
                        if (random.nextBoolean()) {
                            drawRect(
                                color = if (random.nextInt(4) == 0) CyberPrimary else Color.Black,
                                topLeft = androidx.compose.ui.geometry.Offset(col * cellSize, row * cellSize),
                                size = androidx.compose.ui.geometry.Size(cellSize * 0.9f, cellSize * 0.9f)
                            )
                        }
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.dp, CyberSecondary, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                "QRIS", 
                color = CyberSecondary, 
                fontWeight = FontWeight.Bold, 
                fontSize = 11.sp, 
                fontFamily = FontFamily.SansSerif
            )
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
    val isAdminLoggedIn by viewModel.isAdminLoggedIn.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val tokopayInvoice by viewModel.tokopayInvoiceState.collectAsStateWithLifecycle()
    val isTokopayLoading by viewModel.isTokopayLoading.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    val context = LocalContext.current

    if (!isAdminLoggedIn) {
        // --- GUEST STATE / LOGIN PAGE ---
        var emailInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(CyberPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = CyberPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Administrator Portal",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = CyberTextPrimary,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    "Autentikasi diperlukan untuk konfigurasi sistem & Tokopay",
                    fontSize = 12.sp,
                    color = CyberTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.dp, CyberCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Masuk sebagai Admin",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CyberPrimary
                        )

                        // Email Field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Admin") },
                            placeholder = { Text("rizakontol@kontol.my.id") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Email Icon",
                                    tint = CyberTextSecondary
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_email_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberPrimary,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedLabelColor = CyberPrimary,
                                unfocusedLabelColor = CyberTextSecondary,
                                focusedTextColor = CyberTextPrimary,
                                unfocusedTextColor = CyberTextPrimary
                            )
                        )

                        // Password Field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password Admin") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password Icon",
                                    tint = CyberTextSecondary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Close else Icons.Default.Refresh,
                                        contentDescription = "Toggle password view"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberPrimary,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedLabelColor = CyberPrimary,
                                unfocusedLabelColor = CyberTextSecondary,
                                focusedTextColor = CyberTextPrimary,
                                unfocusedTextColor = CyberTextPrimary
                            )
                        )

                        // Submit Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Convenience Autobutton
                            OutlinedButton(
                                onClick = {
                                    emailInput = "rizakontol@kontol.my.id"
                                    passwordInput = "rizakontol"
                                    Toast.makeText(context, "Kredensial Pengujian Terisi!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, CyberSecondary),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberSecondary)
                            ) {
                                Text("Isi Otomatis", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val success = viewModel.loginAdmin(emailInput, passwordInput)
                                    if (success) {
                                        Toast.makeText(context, "Login Berhasil! Selamat datang Admin.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Gagal Login. Periksa Input!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("admin_login_submit_button"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary, contentColor = Color.White)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Sign In", modifier = Modifier.size(16.dp))
                                    Text("MASUK ADMIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Login Error Notice
                        if (loginError != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberError.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                    .border(1.dp, CyberError, RoundedCornerShape(6.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Error Logo", tint = CyberError, modifier = Modifier.size(16.dp))
                                    Text(loginError!!, fontSize = 11.sp, color = CyberError, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
                    border = BorderStroke(1.dp, CyberCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = CyberSecondary, modifier = Modifier.size(16.dp))
                            Text("Kredensial Akun Admin Pengujian:", fontWeight = FontWeight.Bold, color = CyberTextPrimary, fontSize = 12.sp)
                        }
                        Text(
                            "Email: rizakontol@kontol.my.id\nPassword: rizakontol", 
                            fontFamily = FontFamily.Monospace, 
                            color = CyberSecondary, 
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                    }
                }
            }
        }
    } else {
        // --- ADMIN AUTHORIZED STATE ---
        var localToken by remember(settingsToken) { mutableStateOf(settingsToken) }
        var localGeminiKey by remember(settingsGeminiKey) { mutableStateOf(settingsGeminiKey) }
        var localInstruction by remember(settingsInstruction) { mutableStateOf(settingsInstruction) }

        var tokenObscured by remember { mutableStateOf(true) }
        var keyObscured by remember { mutableStateOf(true) }

        // Local states for Tokopay CONFIG
        var tokopayMerchant by remember(settings.tokopayMerchantId) { mutableStateOf(settings.tokopayMerchantId) }
        var tokopaySecret by remember(settings.tokopaySecretKey) { mutableStateOf(settings.tokopaySecretKey) }
        var tokopayActive by remember(settings.tokopayIsActive) { mutableStateOf(settings.tokopayIsActive) }
        var tokopaySecretObscured by remember { mutableStateOf(true) }

        // Local states for Invoice SIMULATOR
        var simAmount by remember { mutableStateOf("15000") }
        var selectedMethod by remember { mutableStateOf("QRIS") }
        val paymentMethods = listOf("QRIS", "Virtual Account Mandiri", "Virtual Account BCA")

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Admin Session Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CyberPrimary)
                        )
                        Text(
                            text = "Admin: rizakontol@kontol.my.id",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextPrimary
                        )
                    }

                    TextButton(
                        onClick = { viewModel.logoutAdmin() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = CyberError)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Keluar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Keluar (Logout)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Server Control Bot Card (Original)
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

            // Credentials Config Card (Original)
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
                            "Konfigurasi Kredensial Bot & Gemini",
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

            // TOKOPAY GATEWAY CONFIGURATION CARD (EXCLUSIVELY MANAGED BY ADMIN)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.dp, CyberCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Payment Gateway Icon",
                                tint = CyberSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Payment Gateway Tokopay (Atur Harus Admin)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CyberSecondary
                            )
                        }

                        Text(
                            "Kelola setelan merchant, api key secret, dan webhook instant untuk mengaktifkan pembayaran otomatis dari sistem Tokopay Indonesia.",
                            fontSize = 11.sp,
                            color = CyberTextSecondary
                        )

                        // Merchant ID Input
                        OutlinedTextField(
                            value = tokopayMerchant,
                            onValueChange = { tokopayMerchant = it },
                            label = { Text("Merchant ID Tokopay") },
                            placeholder = { Text("Masukkan Merchant ID, contoh: MC10234") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Merchant ID Icon",
                                    tint = CyberTextSecondary
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tokopay_merchant_id_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberSecondary,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedLabelColor = CyberSecondary,
                                unfocusedLabelColor = CyberTextSecondary,
                                focusedTextColor = CyberTextPrimary,
                                unfocusedTextColor = CyberTextPrimary
                            )
                        )

                        // Secret Key Input
                        OutlinedTextField(
                            value = tokopaySecret,
                            onValueChange = { tokopaySecret = it },
                            label = { Text("Secret / API Key Tokopay") },
                            placeholder = { Text("Masukkan secret key rahasia") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Secret Key Icon",
                                    tint = CyberTextSecondary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { tokopaySecretObscured = !tokopaySecretObscured }) {
                                    Icon(
                                        imageVector = if (tokopaySecretObscured) Icons.Default.Lock else Icons.Default.Refresh,
                                        contentDescription = "Toggle secret view"
                                    )
                                }
                            },
                            visualTransformation = if (tokopaySecretObscured) PasswordVisualTransformation() else VisualTransformation.None,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tokopay_secret_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberSecondary,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedLabelColor = CyberSecondary,
                                unfocusedLabelColor = CyberTextSecondary,
                                focusedTextColor = CyberTextPrimary,
                                unfocusedTextColor = CyberTextPrimary
                            )
                        )

                        // Active State Switch Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Status Tokopay Gateway",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CyberTextPrimary
                                )
                                Text(
                                    text = if (tokopayActive) "Aktif (Menerima Pembayaran)" else "Nonaktif (Off)",
                                    fontSize = 11.sp,
                                    color = if (tokopayActive) CyberPrimary else CyberTextSecondary
                                )
                            }

                            Switch(
                                checked = tokopayActive,
                                onCheckedChange = { tokopayActive = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberPrimary,
                                    checkedTrackColor = CyberPrimary.copy(alpha = 0.35f),
                                    uncheckedThumbColor = CyberTextSecondary,
                                    uncheckedTrackColor = CyberCardBorder
                                )
                            )
                        }

                        // Save Tokopay settings button
                        Button(
                            onClick = {
                                viewModel.saveTokopaySettings(tokopayMerchant, tokopaySecret, tokopayActive)
                                Toast.makeText(context, "Setelan Tokopay tersimpan!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("tokopay_save_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary, contentColor = Color.White)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Simpan", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SIMPAN KONFIGURASI TOKOPAY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // TOKOPAY INSTANT INVOICE GENERATOR & WEBHOOK SIMULATOR CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.dp, CyberCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Uji Icon",
                                tint = CyberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Uji Generator Invoice Tokopay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CyberPrimary
                            )
                        }

                        Text(
                            "Uji flow order transaksi Tokopay. Administrator dapat meluncurkan invoice uji coba secara realtime dan memverifikasi integrasi callback webhook langsung dalam console.",
                            fontSize = 11.sp,
                            color = CyberTextSecondary
                        )

                        // Amount field
                        OutlinedTextField(
                            value = simAmount,
                            onValueChange = { simAmount = it.filter { char -> char.isDigit() } },
                            label = { Text("Jumlah Pembayaran (IDR / Rupiah)") },
                            placeholder = { Text("15000") },
                            leadingIcon = {
                                Text("Rp", style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, color = CyberSecondary), modifier = Modifier.padding(start = 12.dp, end = 4.dp))
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tokopay_test_amount_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberPrimary,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedTextColor = CyberTextPrimary,
                                unfocusedTextColor = CyberTextPrimary
                            )
                        )

                        // Payment Methods chips selection
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Pilih Metode Bayar Tokopay:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberTextPrimary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                paymentMethods.forEach { method ->
                                    val isSel = (method.startsWith("QRIS") && selectedMethod == "QRIS") || 
                                                (method.contains("Mandiri") && selectedMethod == "MANDIRI") ||
                                                (method.contains("BCA") && selectedMethod == "BCA")
                                    
                                    val code = when {
                                        method.startsWith("QRIS") -> "QRIS"
                                        method.contains("Mandiri") -> "MANDIRI"
                                        else -> "BCA"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) CyberPrimary else CyberSurfaceVariant)
                                            .border(1.dp, if (isSel) CyberPrimary else CyberCardBorder, RoundedCornerShape(8.dp))
                                            .clickable { selectedMethod = code }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (code == "QRIS") "QRIS" else if (code == "MANDIRI") "VA Mandiri" else "VA BCA",
                                            color = if (isSel) Color.White else CyberTextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Generate Button
                        Button(
                            onClick = {
                                val amtNum = simAmount.toIntOrNull() ?: 15000
                                viewModel.createTokopayInvoice(amtNum, selectedMethod)
                            },
                            enabled = !isTokopayLoading && simAmount.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("tokopay_generate_invoice_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary, contentColor = Color.White)
                        ) {
                            if (isTokopayLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Generate", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TERBITKAN INVOICE TOKOPAY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        // Display active invoice details
                        if (tokopayInvoice != null) {
                            HorizontalDivider(color = CyberCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberSurfaceVariant, RoundedCornerShape(10.dp))
                                    .border(1.dp, CyberPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Status Badge Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tokopayInvoice!!.refId,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTextPrimary,
                                            fontSize = 14.sp
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    if (tokopayInvoice!!.status == "PAID") CyberPrimary.copy(alpha = 0.15f)
                                                    else Color(0xFFFF9800).copy(alpha = 0.15f)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (tokopayInvoice!!.status == "PAID") CyberPrimary else Color(0xFFFF9800),
                                                    RoundedCornerShape(20.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (tokopayInvoice!!.status == "PAID") "LUNAS (PAID)" else "PENDING",
                                                color = if (tokopayInvoice!!.status == "PAID") CyberPrimary else Color(0xFFFF9800),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Invoice Amount Display
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("TOTAL TAGIHAN", fontSize = 10.sp, color = CyberTextSecondary)
                                        Text(
                                            "Rp ${tokopayInvoice!!.amount}",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CyberTextPrimary
                                        )
                                        Text("Metode: ${tokopayInvoice!!.paymentMethod}", fontSize = 11.sp, color = CyberTextSecondary)
                                    }

                                    // Render visual depending on QRIS vs Virtual Account
                                    if (tokopayInvoice!!.paymentMethod == "QRIS") {
                                        QrisQrCodeVisual(amount = tokopayInvoice!!.amount)
                                        Text("Pindai QRIS menggunakan aplikasi banking / e-wallet Anda.", fontSize = 10.sp, color = CyberTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    } else {
                                        // Transfer virtual account visual instruction card
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CyberSurface, RoundedCornerShape(8.dp))
                                                .border(0.5.dp, CyberCardBorder, RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    "INTRUKSI TRANSFER VIRTUAL ACCOUNT",
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyberSecondary,
                                                    fontSize = 11.sp
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Nomor VA:", fontSize = 11.sp, color = CyberTextSecondary)
                                                    Text(
                                                        "8879" + System.currentTimeMillis().toString().takeLast(8),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CyberTextPrimary,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Text("Lakukan pembayaran lewat ATM, M-Banking, atau internet banking sesuai tagihan di atas.", fontSize = 10.sp, color = CyberTextSecondary)
                                            }
                                        }
                                    }

                                    // Webhook test pay button
                                    if (tokopayInvoice!!.status == "PENDING") {
                                        Button(
                                            onClick = { viewModel.simulatePaymentSuccess() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("tokopay_test_pay_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Test payment webhook check", modifier = Modifier.size(18.dp))
                                                Text("BAYAR INSTAN (UJI WEBHOOK)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CyberPrimary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Selesai! callback webhook tokopay sukses diverifikasi murni.",
                                                color = CyberPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
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
                        label = { Text("Kirim Pesan Uji Coba") },
                        placeholder = { Text("Tulis pesan uji coba seolah dikirim oleh pengguna Telegram...") },
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
