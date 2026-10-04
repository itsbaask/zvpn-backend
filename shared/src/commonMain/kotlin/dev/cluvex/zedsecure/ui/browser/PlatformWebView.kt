package dev.cluvex.zedsecure.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformWebView(tab: BrowserTabState, controller: BrowserController, modifier: Modifier)
