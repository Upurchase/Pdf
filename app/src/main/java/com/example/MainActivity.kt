package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.PdfViewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle opening PDF if launched via ACTION_VIEW
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val readerState by viewModel.readerState.collectAsState()

                    Crossfade(
                        targetState = readerState.currentItem != null,
                        label = "ScreenTransition"
                    ) { isReading ->
                        if (isReading) {
                            ReaderScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.closeReader() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LibraryScreen(
                                viewModel = viewModel,
                                onOpenPdfItem = { item -> viewModel.openPdfItem(item) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.data

        if ((action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) && data != null) {
            viewModel.openPdfFromUri(data)
        }
    }
}
