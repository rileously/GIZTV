package androidx.compose.ui.viewinterop

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun <T : Any> AndroidView(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {},
) {
    val context = LocalContext.current
    val view = factory(context)
    update(view)
    Box(modifier = modifier)
}
