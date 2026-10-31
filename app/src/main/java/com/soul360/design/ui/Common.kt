package com.soul360.design.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Palette {
    val Cream = Color(0xFFF7F2EB)
    val Sand = Color(0xFFEDE4D7)
    val Stone = Color(0xFFD9CEBF)
    val Taupe = Color(0xFFA39383)
    val Ink = Color(0xFF1E1A17)
    val Charcoal = Color(0xFF2B2521)
    val Orange = Color(0xFFE8772E)
    val OrangeDeep = Color(0xFFC85A16)
}

private val colors: ColorScheme = lightColorScheme(
    primary = Palette.Orange, onPrimary = Color.White,
    secondary = Palette.Taupe, onSecondary = Color.White,
    background = Palette.Cream, onBackground = Palette.Ink,
    surface = Palette.Cream, onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand, onSurfaceVariant = Palette.Ink,
    surfaceContainer = Palette.Sand, surfaceContainerHigh = Palette.Sand,
    surfaceContainerLow = Palette.Cream, surfaceContainerLowest = Color.White,
    secondaryContainer = Palette.Sand, onSecondaryContainer = Palette.Ink,
    outline = Palette.Stone, outlineVariant = Palette.Stone,
)

private val baseType = Typography()
private val type = Typography(
    displayLarge = baseType.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = baseType.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = baseType.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = baseType.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = baseType.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = baseType.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = baseType.titleLarge.copy(fontFamily = FontFamily.Serif),
    titleMedium = baseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = baseType.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
)

@Composable
fun Soul360Theme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = type, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp),
    ), content = content)

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
            Spacer(Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelMedium, color = Palette.Taupe)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(title) },
        text = { Text(message) },
    )
}

fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

fun exportFile(context: Context, name: String): File =
    File(context.cacheDir, "exports").apply { mkdirs() }.let { File(it, name) }

fun writePng(context: Context, bitmap: Bitmap, name: String): File =
    exportFile(context, name).apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Saves to Pictures/SOUL 360 DESIGN. Needs no permission on Android 10+. */
fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        shareFile(context, writePng(context, bitmap, name), "image/png")
        return false
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SOUL 360 DESIGN")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    Toast.makeText(context, "Saved to Pictures › SOUL 360 DESIGN", Toast.LENGTH_SHORT).show()
    return true
}

fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (e2: ActivityNotFoundException) { Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun HSpace(w: Int = 8) = Spacer(Modifier.width(w.dp))

fun Modifier.screenPadding() = this.padding(horizontal = 16.dp)
