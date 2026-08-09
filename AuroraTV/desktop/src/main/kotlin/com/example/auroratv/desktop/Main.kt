package com.example.auroratv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.auroratv.ui.AuroraTvRoot

fun main() = application {
    val windowState = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        width = 1280.dp,
        height = 720.dp,
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Aurora TV - Desktop Edition",
        state = windowState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A111D))
        ) {
            AuroraTvRoot()
        }
    }
}
