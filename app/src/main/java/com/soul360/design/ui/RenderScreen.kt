@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.soul360.design.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.soul360.design.ai.AiImage
import com.soul360.design.ai.DirectAI
import com.soul360.design.ai.RenderStyles
import com.soul360.design.data.AiProvider
import com.soul360.design.data.AppSettings
import com.soul360.design.image.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Full-screen AI render: sends the composed room (all placed furniture) plus each furniture
 * image to ChatGPT or Gemini and shows a before/after result.
 */
@Composable
fun RenderScreen(settings: AppSettings, composite: Bitmap, furniture: List<Bitmap>, materials: List<Pair<Bitmap, String>> = emptyList(), onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var provider by remember { mutableStateOf(settings.provider) }
    var hasKey by remember(provider) { mutableStateOf(settings.hasApiKey(provider)) }
    var keyInput by remember(provider) { mutableStateOf("") }
    var styleId by remember { mutableStateOf("modern") }
    var lightingIdx by remember { mutableIntStateOf(0) }
    var restyle by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val style = RenderStyles.all.first { it.id == styleId }
    val compositeImage = remember(composite) { composite.asImageBitmap() }

    BackHandler {
        when {
            running -> {}
            result != null -> result = null
            else -> onClose()
        }
    }

    LaunchedEffect(running) {
        elapsed = 0
        while (running) { delay(1000); elapsed++ }
    }

    fun render() {
        val key = settings.apiKey(provider)
        if (key == null) { hasKey = false; error = "Add your ${provider.label} API key first."; return }
        settings.provider = provider
        running = true
        job = scope.launch {
            try {
                val images = withContext(Dispatchers.Default) {
                    val list = mutableListOf(AiImage(jpeg(ImageTools.downscale(composite, 1536)), "image/jpeg"))
                    furniture.take(4).forEach { list += AiImage(png(ImageTools.downscale(it, 768)), "image/png") }
                    materials.take(3).forEach { list += AiImage(jpeg(ImageTools.downscale(it.first, 768)), "image/jpeg") }
                    list
                }
                val furnCount = furniture.take(4).size
                val prompt = RenderStyles.buildPrompt(style, RenderStyles.lighting[lightingIdx].second, restyle, notes, furnCount,
                    materials.take(3).map { it.second })
                val bytes = when (provider) {
                    AiProvider.OPENAI -> DirectAI.openAiEdit(key, settings.openaiModel, images, prompt)
                    AiProvider.GEMINI -> DirectAI.geminiEdit(key, settings.geminiModel, images, prompt)
                }
                result = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    ?: throw IllegalStateException("The AI returned an image this phone can't read.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // user stopped waiting
            } catch (e: Exception) {
                error = e.message ?: "Render failed."
            } finally {
                running = false; job = null
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Cream, Palette.Sand)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!running) { if (result != null) result = null else onClose() } }) {
                    Icon(Icons.Filled.Close, "Close", tint = Palette.Ink)
                }
                Text("AI Render", style = MaterialTheme.typography.titleLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                ProviderToggle(provider, enabled = !running) { provider = it }
            }

            val r = result
            if (r != null) {
                val appear = remember(r) { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(r) { appear.animateTo(1f, androidx.compose.animation.core.tween(600)) }
                ResultView(before = composite, after = r, modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                    .androidx_alpha(appear.value))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { result = null }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Ink)) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); HSpace(6); Text("New style")
                    }
                    OutlinedButton(onClick = { shareFile(context, writePng(context, r, "Soul360-AI-${timestamp()}.png"), "image/png") },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Ink)) {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); HSpace(6); Text("Share")
                    }
                    Button(onClick = { saveToGallery(context, r, "Soul360-AI-${timestamp()}.png") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); HSpace(6); Text("Save")
                    }
                }
                Text("Drag the slider to compare before and after. AI can change small details — check before sharing.",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Palette.Taupe, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Image(compositeImage, "Your design", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).clip(MaterialTheme.shapes.medium)
                            .aspectRatio(composite.width.toFloat() / composite.height))

                    SectionLabel("Style")
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(RenderStyles.all, key = { it.id }) { s ->
                            val selected = s.id == styleId
                            Column(
                                Modifier.width(132.dp).clip(MaterialTheme.shapes.medium)
                                    .background(Color.White)
                                    .border(2.dp, if (selected) Palette.Orange else Color.Transparent, MaterialTheme.shapes.medium)
                                    .clickable(enabled = !running) { styleId = s.id }
                                    .padding(10.dp)
                            ) {
                                Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(s.swatch.map { Color(it) })))
                                Spacer(Modifier.height(8.dp))
                                Text(s.title, color = Palette.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Text(s.subtitle, color = Palette.Taupe, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }

                    SectionLabel("Lighting")
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RenderStyles.lighting.size) { i ->
                            FilterChip(
                                selected = i == lightingIdx, onClick = { lightingIdx = i }, enabled = !running,
                                label = { Text(RenderStyles.lighting[i].first) },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Palette.Ink, selectedContainerColor = Palette.Orange, selectedLabelColor = Color.White),
                            )
                        }
                    }

                    if (style.allowsRestyle) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Refresh walls & decor", color = Palette.Ink, style = MaterialTheme.typography.labelLarge)
                                Text("Let AI restyle wall colour, floor, rugs and decor to the style",
                                    color = Palette.Taupe, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(restyle, { restyle = it }, enabled = !running,
                                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Orange))
                        }
                    }

                    OutlinedTextField(
                        notes, { notes = it }, enabled = !running,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        label = { Text("Extra wishes (optional)") },
                        placeholder = { Text("e.g. add a large plant by the window, beige curtains") },
                        colors = darkFieldColors(), minLines = 2,
                    )

                    if (!hasKey) {
                        Column(Modifier.padding(12.dp).clip(MaterialTheme.shapes.medium).background(Color.White).padding(14.dp)) {
                            Text("Connect ${provider.label}", color = Palette.Ink, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (provider == AiProvider.OPENAI) "Paste your OpenAI API key (from platform.openai.com → API keys). Billing must be enabled on your OpenAI account."
                                else "Paste your Gemini API key (from aistudio.google.com → Get API key). Image models may need billing enabled.",
                                color = Palette.Taupe, style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(keyInput, { keyInput = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                                label = { Text("API key") }, singleLine = true, colors = darkFieldColors(),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                            Button(onClick = {
                                try { settings.saveApiKey(provider, keyInput); keyInput = ""; hasKey = true; error = null }
                                catch (e: Exception) { error = e.message }
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save key on this phone") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Button(
                        onClick = { render() }, enabled = hasKey && !running,
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null); HSpace(8)
                        Text("Render with ${provider.label}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Your design${if (furniture.isNotEmpty()) ", ${furniture.size.coerceAtMost(4)} item image(s)" else ""}${if (materials.isNotEmpty()) " and ${materials.size.coerceAtMost(3)} material(s)" else ""} will be sent to ${provider.label}. Each render uses your API credits.",
                        Modifier.padding(top = 6.dp), color = Palette.Taupe, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = running,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)),
        ) {
            Box(Modifier.fillMaxSize().background(Palette.Cream.copy(alpha = 0.95f))
                .pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    LogoLoader(Modifier.size(110.dp))
                    Spacer(Modifier.height(20.dp))
                    Text("${provider.label} is rendering your room…", color = Palette.Ink, style = MaterialTheme.typography.titleMedium)
                    Text("${style.title} · ${RenderStyles.lighting[lightingIdx].first} · ${elapsed}s",
                        color = Palette.Brown, style = MaterialTheme.typography.bodySmall)
                    Text("This usually takes 20–90 seconds. Keep the app open.",
                        Modifier.padding(top = 8.dp), color = Palette.Taupe, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { job?.cancel() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Ink)) {
                        Text("Stop waiting")
                    }
                }
            }
        }
        error?.let { MessageDialog("AI Render", it) { error = null } }
    }
}

@Composable
private fun ProviderToggle(selected: AiProvider, enabled: Boolean, onSelect: (AiProvider) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Color.White).padding(3.dp)) {
        AiProvider.entries.forEach { p ->
            val on = p == selected
            Text(
                p.label,
                color = if (on) Color.White else Palette.Brown,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape)
                    .background(if (on) Palette.Orange else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(p) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text.uppercase(), Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
        color = Palette.Taupe, style = MaterialTheme.typography.labelMedium)

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.Ink, unfocusedTextColor = Palette.Ink, disabledTextColor = Palette.Taupe,
    focusedBorderColor = Palette.Orange, unfocusedBorderColor = Palette.Taupe,
    focusedLabelColor = Palette.Orange, unfocusedLabelColor = Palette.Brown,
    focusedPlaceholderColor = Palette.Taupe, unfocusedPlaceholderColor = Palette.Taupe,
    cursorColor = Palette.Orange,
)

/** Before/after comparison with a draggable divider. */
@Composable
private fun ResultView(before: Bitmap, after: Bitmap, modifier: Modifier) {
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg = remember(after) { after.asImageBitmap() }
    var split by remember { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = after.width.toFloat() / after.height
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val w = if (boxW / boxH > aspect) boxH * aspect else boxW
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            Modifier.size(with(density) { w.toDp() }, with(density) { (w / aspect).toDp() })
                .clip(MaterialTheme.shapes.medium)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) }
                }
        ) {
            Image(afterImg, "AI render", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Image(beforeImg, "Before", contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                })
            Box(Modifier.fillMaxHeight().width(2.dp).offset { IntOffset((w * split).roundToInt() - 1, 0) }.background(Color.White))
            Box(
                Modifier.align(Alignment.CenterStart).offset { IntOffset((w * split).roundToInt() - 18 * density.density.roundToInt(), 0) }
                    .size(36.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Text("⟷", color = Palette.Ink) }
            Text("BEFORE", Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 3.dp),
                color = Color.White, style = MaterialTheme.typography.labelSmall)
            Text("AFTER", Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape)
                .background(Palette.Orange).padding(horizontal = 8.dp, vertical = 3.dp),
                color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Modifier.androidx_alpha(a: Float): Modifier =
    this.then(Modifier.graphicsLayer { alpha = a; scaleX = 0.96f + 0.04f * a; scaleY = 0.96f + 0.04f * a })

private fun jpeg(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
private fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
