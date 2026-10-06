package com.zvpn.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformWebView(tab: BrowserTabState, controller: BrowserController, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Browser is not supported on desktop yet") }
}
