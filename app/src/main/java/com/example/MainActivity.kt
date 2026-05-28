package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.data.AppDatabase
import com.example.data.BotRepository
import com.example.data.BotService
import com.example.ui.BotScreen
import com.example.ui.BotViewModel
import com.example.ui.BotViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core Offline Persistence & Repository Layers
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = BotRepository(database.botDao())

        // Autorestart the 24-Hour Foreground Service on relaunch if enabled in DB configurations
        lifecycleScope.launch {
            val settings = repository.getSettings()
            if (settings != null && settings.isBotRunning && settings.telegramToken.isNotBlank()) {
                BotService.startService(applicationContext)
            }
        }

        setContent {
            MyApplicationTheme {
                val viewModel: BotViewModel = viewModel(
                    factory = BotViewModelFactory(repository)
                )

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BotScreen(viewModel = viewModel)
                }
            }
        }
    }
}
