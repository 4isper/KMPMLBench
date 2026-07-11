package com.m4isper.kmpmlbench

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KMPMLBench",
    ) {
        App()
    }
}