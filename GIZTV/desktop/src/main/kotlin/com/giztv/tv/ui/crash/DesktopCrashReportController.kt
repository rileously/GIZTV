package com.giztv.tv.ui.crash

import androidx.compose.runtime.Composable

/**
 * Stands in for the phone's crash panel, which is built out of an Android share sheet and clipboard.
 *
 * The shared root calls this on every platform, so desktop needs the name even though it has
 * nothing to show: the reporter is installed from MainActivity, which desktop does not compile.
 */
@Composable
internal fun CrashReportController() {
    // Desktop crash reporting handler
}
