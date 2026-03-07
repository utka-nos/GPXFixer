package com.gpxeditor.shared

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController(
    configure = { enforceStrictPlistSanityCheck = false }
) { App() }

class ComposeEntryPoint {
    fun makeViewController() = MainViewController()
}
