package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.BotRepository
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
