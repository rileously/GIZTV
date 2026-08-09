package androidx.tv.material3

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

typealias Typography = androidx.compose.material3.Typography
typealias ColorScheme = androidx.compose.material3.ColorScheme

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = TextStyle.Default,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

fun darkColorScheme(
    primary: Color = Color.Unspecified,
    onPrimary: Color = Color.Unspecified,
    primaryContainer: Color = Color.Unspecified,
    onPrimaryContainer: Color = Color.Unspecified,
    secondary: Color = Color.Unspecified,
    onSecondary: Color = Color.Unspecified,
    secondaryContainer: Color = Color.Unspecified,
    onSecondaryContainer: Color = Color.Unspecified,
    tertiary: Color = Color.Unspecified,
    onTertiary: Color = Color.Unspecified,
    tertiaryContainer: Color = Color.Unspecified,
    onTertiaryContainer: Color = Color.Unspecified,
    error: Color = Color.Unspecified,
    onError: Color = Color.Unspecified,
    errorContainer: Color = Color.Unspecified,
    onErrorContainer: Color = Color.Unspecified,
    background: Color = Color.Unspecified,
    onBackground: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    onSurface: Color = Color.Unspecified,
    surfaceVariant: Color = Color.Unspecified,
    onSurfaceVariant: Color = Color.Unspecified,
    outline: Color = Color.Unspecified,
    outlineVariant: Color = Color.Unspecified,
    scrim: Color = Color.Unspecified,
): ColorScheme = androidx.compose.material3.darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    scrim = scrim,
)

@Composable
fun MaterialTheme(
    colorScheme: ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme,
    typography: Typography = androidx.compose.material3.MaterialTheme.typography,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

object MaterialTheme {
    val colorScheme: ColorScheme
        @Composable
        get() = androidx.compose.material3.MaterialTheme.colorScheme

    val typography: Typography
        @Composable
        get() = androidx.compose.material3.MaterialTheme.typography
}
