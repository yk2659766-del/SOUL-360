@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.soul360.design.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.soul360.design.ai.GatewayClient
import com.soul360.design.data.AppSettings
import com.soul360.design.data.FurnitureLayer
import com.soul360.design.data.PhotoProject
import com.soul360.design.data.ProjectStore
import com.soul360.design.image.BackgroundRemover
import com.soul360.design.image.Compositor
import com.soul360.design.image.ImageTools
import com.soul360.design.image.LayerGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import kotlin.math.roundToInt

private const val DEFAULT_RENDER_PROMPT =
    "Photorealistic interior photograph. Keep the room architecture, camera angle, walls, windows and floor " +
        "exactly as they are. Keep every added furniture item in the same position, size and style, but blend it " +
        "naturally with matching perspective, lighting, reflections and soft contact shadows."

private sealed interface AiState {
    data class ConfirmRender(val prompt: String) : AiState
    data class ConfirmModel(val layer: FurnitureLayer) : AiState
    data class Running(val title: String, val status: String) : AiState
    data class RenderResult(val image: Bitmap) : AiState
}

@Composable
fun EditorScreen(store: ProjectStore, settings: AppSettings, initial: PhotoProject, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf(initial) }
    var room by remember { mutableStateOf<Bitmap?>(null) }
    val images = remember { mutableStateMapOf<String, Bitmap>() }
    val shadows = remember { mutableStateMapOf<String, Bitmap>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf<AiState?>(null) }
    var aiJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(initial.id) {
        val loaded = withContext(Dispatchers.IO) {
            initial.layers.mapNotNull { l ->
                store.layerImage(initial, l.imageFile)?.let { bmp -> Triple(l.imageFile, bmp, ImageTools.shadowSilhouette(bmp)) }
            }
        }
        loaded.forEach { (name, bmp, sh) -> images[name] = bmp; shadows[name] = sh }
        room = withContext(Dispatchers.IO) { store.roomImage(initial) }
    }

    fun update(id: String, change: (FurnitureLayer) -> FurnitureLayer) {
        project = project.copy(layers = project.layers.map { if (it.id == id) change(it) else it })
    }

    fun persist(reportErrors: Boolean) {
        val r = room ?: return
        try {
            val p = project.copy(modified = System.currentTimeMillis())
            val thumb = Compositor.render(r, p.layers, images, shadows, 600)
            store.save(p, thumb)
            store.pruneUnusedLayers(p)
            project = p
        } catch (e: Exception) {
            if (reportErrors) message = "Save failed" to (e.message ?: "Unknown error")
        }
    }

    fun close() { persist(reportErrors = false); onClose() }
    BackHandler { if (aiJob == null) close() }

    // Autosave when the app goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) persist(false) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    suspend fun addLayer(bmp: Bitmap) {
        val p = project
        val (name, saved, sh) = withContext(Dispatchers.IO) {
            val n = store.writeLayerImage(p, bmp)
            val saved = store.layerImage(p, n) ?: bmp
            Triple(n, saved, ImageTools.shadowSilhouette(saved))
        }
        images[name] = saved
        shadows[name] = sh
        val offset = (project.layers.size % 5) * 0.04f
        val layer = FurnitureLayer(imageFile = name, centerX = 0.5f + offset, centerY = 0.62f + offset)
        project = project.copy(layers = project.layers + layer)
        selectedId = layer.id
    }

    val pickFurniture = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = "Adding furniture…"
        scope.launch {
            try {
                uris.forEach { uri ->
                    val bmp = withContext(Dispatchers.IO) { ImageTools.decode(context, uri, 2048) }
                    addLayer(bmp)
                }
            } catch (e: Exception) {
                message = "Could not add image" to (e.message ?: "")
            } finally { busy = null }
        }
    }

    fun cutout(layer: FurnitureLayer) {
        val src = images[layer.imageFile] ?: return
        busy = "Removing background…"
        scope.launch {
            try {
                val cut = BackgroundRemover.removeBackground(src)
                val name = withContext(Dispatchers.IO) { store.writeLayerImage(project, cut) }
                images[name] = cut
                shadows[name] = withContext(Dispatchers.Default) { ImageTools.shadowSilhouette(cut) }
                // Keep the on-screen height similar when the cutout is narrower than the original.
                val oldAspect = src.height.toFloat() / src.width
                val newAspect = cut.height.toFloat() / cut.width
                update(layer.id) { it.copy(imageFile = name, widthFraction = (it.widthFraction * oldAspect / newAspect).coerceIn(0.03f, 2f)) }
            } catch (e: Exception) {
                message = "Background removal" to (e.message ?: "Failed")
            } finally { busy = null }
        }
    }

    fun exportImage(toGallery: Boolean) {
        val r = room ?: return
        busy = "Exporting…"
        scope.launch {
            try {
                val out = withContext(Dispatchers.Default) { Compositor.render(r, project.layers, images, shadows) }
                val name = "Soul360-${timestamp()}.png"
                if (toGallery) saveToGallery(context, out, name)
                else shareFile(context, withContext(Dispatchers.IO) { writePng(context, out, name) }, "image/png")
            } catch (e: Exception) {
                message = "Export failed" to (e.message ?: "")
            } finally { busy = null }
        }
    }

    fun gatewayOrNull(): GatewayClient? {
        val token = settings.token()
        if (settings.gatewayUrl.isEmpty() || token == null) {
            message = "AI is not set up" to "Add your gateway address and app token in Settings. Photo editing and cutouts work without AI."
            return null
        }
        return GatewayClient(settings.gatewayUrl, token)
    }

    fun runRender(prompt: String) {
        val client = gatewayOrNull() ?: return
        val r = room ?: return
        aiJob = scope.launch {
            try {
                ai = AiState.Running("AI render", "Preparing…")
                val png = withContext(Dispatchers.Default) {
                    val comp = Compositor.render(r, project.layers, images, shadows, 1536)
                    ByteArrayOutputStream().also { comp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                }
                val bytes = client.run(GatewayClient.Kind.RENDER, png, prompt) { s -> ai = AiState.Running("AI render", s) }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("The gateway returned an unreadable image.")
                ai = AiState.RenderResult(bmp)
            } catch (e: kotlinx.coroutines.CancellationException) {
                ai = null
            } catch (e: Exception) {
                ai = null; message = "AI render failed" to (e.message ?: "")
            } finally { aiJob = null }
        }
    }

    fun runModel(layer: FurnitureLayer) {
        val client = gatewayOrNull() ?: return
        val img = images[layer.imageFile] ?: return
        aiJob = scope.launch {
            try {
                ai = AiState.Running("Image to 3D", "Preparing…")
                val png = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().also { ImageTools.downscale(img, 1024).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                }
                val bytes = client.run(GatewayClient.Kind.MODEL, png, null) { s -> ai = AiState.Running("Image to 3D", s) }
                val file = withContext(Dispatchers.IO) { store.saveModel(bytes, "Furniture ${timestamp()}") }
                ai = null
                message = "3D model saved" to "Saved as \"${file.nameWithoutExtension}\". Open 3D Studio from the home screen to place it in a room scan."
            } catch (e: kotlinx.coroutines.CancellationException) {
                ai = null
            } catch (e: Exception) {
                ai = null; message = "Image to 3D failed" to (e.message ?: "")
            } finally { aiJob = null }
        }
    }

    val selected = project.layers.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().background(Palette.Cream).statusBarsPadding().navigationBarsPadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close and save") }
            Text(project.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                persist(reportErrors = true)
                if (message == null) android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Filled.Save, "Save") }
            Box {
                IconButton(onClick = { exportMenu = true }) { Icon(Icons.Filled.IosShare, "Export") }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    DropdownMenuItem(text = { Text("Share image…") }, onClick = { exportMenu = false; exportImage(false) })
                    DropdownMenuItem(text = { Text("Save to gallery") }, onClick = { exportMenu = false; exportImage(true) })
                }
            }
        }

        // Canvas
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(8.dp), contentAlignment = Alignment.Center) {
            val r = room
            if (r == null) {
                CircularProgressIndicator()
            } else {
                val density = LocalDensity.current
                val maxW = constraints.maxWidth.toFloat()
                val maxH = constraints.maxHeight.toFloat()
                val scale = minOf(maxW / r.width, maxH / r.height)
                val canvasW = r.width * scale
                val canvasH = r.height * scale
                val roomImage = remember(r) { r.asImageBitmap() }
                Box(
                    Modifier
                        .size(with(density) { canvasW.toDp() }, with(density) { canvasH.toDp() })
                        .pointerInput(Unit) { detectTapGestures { selectedId = null } }
                ) {
                    Image(roomImage, "Room photo", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    if (!showOriginal) {
                        project.layers.forEach { layer -> key(layer.id) {
                            val bmp = images[layer.imageFile]
                            val sb = shadows[layer.imageFile]
                            val layerImage = remember(bmp) { bmp?.asImageBitmap() }
                            val shadowImage = remember(sb) { sb?.asImageBitmap() }
                            if (layerImage != null) {
                                LayerItem(
                                    layer = layer,
                                    image = layerImage,
                                    shadow = shadowImage,
                                    canvasW = canvasW, canvasH = canvasH,
                                    selected = layer.id == selectedId,
                                    onSelect = { selectedId = layer.id },
                                    onTransform = { pan, zoom, rot ->
                                        selectedId = layer.id
                                        update(layer.id) {
                                            it.copy(
                                                centerX = (it.centerX + pan.x / canvasW).coerceIn(-0.2f, 1.2f),
                                                centerY = (it.centerY + pan.y / canvasH).coerceIn(-0.2f, 1.2f),
                                                widthFraction = (it.widthFraction * zoom).coerceIn(0.03f, 2f),
                                                rotation = normalizeDegrees(it.rotation + rot),
                                            )
                                        }
                                    },
                                )
                            }
                        } }
                    }
                }
            }
        }

        // Selected-layer controls
        if (selected != null && !showOriginal) {
            Surface(color = Palette.Sand, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(horizontal = 8.dp)) {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { update(selected.id) { it.copy(flipped = !it.flipped) } },
                            label = { Text("Flip") }, leadingIcon = { Icon(Icons.Filled.Flip, null) })
                        AssistChip(onClick = { cutout(selected) },
                            label = { Text("Remove background") }, leadingIcon = { Icon(Icons.Filled.ContentCut, null) })
                        AssistChip(onClick = {
                            project = project.copy(layers = project.layers.filter { it.id != selected.id } + selected)
                        }, label = { Text("Bring to front") }, leadingIcon = { Icon(Icons.Filled.FlipToFront, null) })
                        AssistChip(onClick = { ai = AiState.ConfirmModel(selected) },
                            label = { Text("Make 3D") }, leadingIcon = { Icon(Icons.Filled.ViewInAr, null) })
                        AssistChip(onClick = {
                            project = project.copy(layers = project.layers.filter { it.id != selected.id }); selectedId = null
                        }, label = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                    }
                    LabeledSlider("Size", selected.widthFraction, 0.03f..1.5f, "${(selected.widthFraction * 100).roundToInt()}%") { v ->
                        update(selected.id) { it.copy(widthFraction = v) }
                    }
                    LabeledSlider("Rotation", selected.rotation, -180f..180f, "${selected.rotation.roundToInt()}°") { v ->
                        update(selected.id) { it.copy(rotation = v) }
                    }
                    LabeledSlider("Brightness", selected.brightness, -0.5f..0.5f, "%+d".format((selected.brightness * 100).roundToInt())) { v ->
                        update(selected.id) { it.copy(brightness = v) }
                    }
                    LabeledSlider("Shadow", selected.shadow, 0f..1f, "${(selected.shadow * 100).roundToInt()}%") { v ->
                        update(selected.id) { it.copy(shadow = v) }
                    }
                }
            }
        }

        // Bottom actions
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = { pickFurniture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("Add furniture") }, leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, null) })
            AssistChip(onClick = { showSearch = true },
                label = { Text("Find online") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
            FilterChip(selected = showOriginal, onClick = { showOriginal = !showOriginal },
                label = { Text("Original") }, leadingIcon = { Icon(Icons.Filled.Compare, null) })
            AssistChip(onClick = { ai = AiState.ConfirmRender(DEFAULT_RENDER_PROMPT) },
                label = { Text("AI render") }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) })
        }
    }

    busy?.let { text ->
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp)); HSpace(12); Text(text)
                }
            }
        }
    }

    message?.let { (t, m) -> MessageDialog(t, m) { message = null } }
    if (showSearch) SearchDialog(onDismiss = { showSearch = false })

    when (val s = ai) {
        is AiState.ConfirmRender -> {
            var prompt by remember { mutableStateOf(s.prompt) }
            AlertDialog(
                onDismissRequest = { ai = null },
                title = { Text("AI render") },
                text = {
                    Column {
                        Text("Your composed room image will be uploaded to your gateway (${settings.gatewayUrl.ifEmpty { "not set" }}) and sent to OpenAI. This can incur charges. AI may change details — check the result.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Instructions") }, minLines = 3)
                    }
                },
                confirmButton = { TextButton(onClick = { ai = null; runRender(prompt) }) { Text("Upload & generate") } },
                dismissButton = { TextButton(onClick = { ai = null }) { Text("Cancel") } },
            )
        }
        is AiState.ConfirmModel -> AlertDialog(
            onDismissRequest = { ai = null },
            title = { Text("Make a 3D model") },
            text = { Text("This furniture image will be uploaded to your gateway and sent to Meshy to generate a GLB model. Hidden sides are estimated and sizes are not exact. This can incur charges. Works best on a cutout with the background removed.") },
            confirmButton = { TextButton(onClick = { ai = null; runModel(s.layer) }) { Text("Upload & generate") } },
            dismissButton = { TextButton(onClick = { ai = null }) { Text("Cancel") } },
        )
        is AiState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text(s.title) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp)); HSpace(12); Text(s.status) }
                    Text("Keep this screen open. Stopping only stops waiting — the provider may still finish and charge.",
                        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
                }
            },
            confirmButton = { TextButton(onClick = { aiJob?.cancel() }) { Text("Stop waiting") } },
        )
        is AiState.RenderResult -> AlertDialog(
            onDismissRequest = { ai = null },
            title = { Text("AI render") },
            text = { Image(s.image.asImageBitmap(), "AI result", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) },
            confirmButton = {
                Row {
                    TextButton(onClick = { saveToGallery(context, s.image, "Soul360-AI-${timestamp()}.png") }) { Text("Save") }
                    TextButton(onClick = { shareFile(context, writePng(context, s.image, "Soul360-AI-${timestamp()}.png"), "image/png") }) { Text("Share") }
                }
            },
            dismissButton = { TextButton(onClick = { ai = null }) { Text("Close") } },
        )
        null -> {}
    }
}

private fun normalizeDegrees(d: Float): Float {
    var v = d % 360f
    if (v > 180f) v -= 360f
    if (v < -180f) v += 360f
    return v
}

@Composable
private fun LayerItem(
    layer: FurnitureLayer,
    image: ImageBitmap,
    shadow: ImageBitmap?,
    canvasW: Float,
    canvasH: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onTransform: (Offset, Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val currentSelect by rememberUpdatedState(onSelect)
    val currentTransform by rememberUpdatedState(onTransform)
    val w = layer.widthFraction * canvasW
    val h = w * image.height / image.width
    val cx = layer.centerX * canvasW
    val cy = layer.centerY * canvasH
    val flip = if (layer.flipped) -1f else 1f

    if (shadow != null && layer.shadow > 0.01f) {
        val padX = w * ImageTools.SHADOW_PAD
        val padY = h * ImageTools.SHADOW_PAD
        val offY = LayerGeometry.shadowOffsetY(layer, canvasW)
        Image(
            shadow, null,
            contentScale = ContentScale.FillBounds,
            alpha = LayerGeometry.shadowAlpha(layer),
            modifier = Modifier
                .offset { IntOffset((cx - w / 2 - padX).roundToInt(), (cy - h / 2 - padY + offY).roundToInt()) }
                .size(with(density) { (w + 2 * padX).toDp() }, with(density) { (h + 2 * padY).toDp() })
                .graphicsLayer { rotationZ = layer.rotation; scaleX = flip },
        )
    }

    val filter = remember(layer.brightness) {
        if (kotlin.math.abs(layer.brightness) > 0.001f) ColorFilter.colorMatrix(ColorMatrix(ImageTools.brightnessMatrix(layer.brightness))) else null
    }
    Image(
        image, "Furniture",
        contentScale = ContentScale.FillBounds,
        colorFilter = filter,
        modifier = Modifier
            .offset { IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .pointerInput(layer.id) { detectTapGestures { currentSelect() } }
            .pointerInput(layer.id) {
                detectTransformGestures { _, pan, zoom, rotation -> currentTransform(pan, zoom, rotation) }
            }
            .graphicsLayer { rotationZ = layer.rotation; scaleX = flip }
            .then(if (selected) Modifier.border(2.dp, Palette.Orange) else Modifier),
    )
}

@Composable
private fun SearchDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    fun q() = URLEncoder.encode(query.ifBlank { "furniture png" }, "UTF-8")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find furniture online") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("e.g. walnut sideboard png") }, singleLine = true)
                Text("Opens in your browser. Long-press an image and choose Download / Save image, then come back and tap Add furniture. A link alone is not an image. Sites may ask you to sign in.",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { openInBrowser(context, "https://www.google.com/search?tbm=isch&q=${q()}") }, label = { Text("Google Images") })
                    AssistChip(onClick = { openInBrowser(context, "https://www.pinterest.com/search/pins/?q=${q()}") }, label = { Text("Pinterest") })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
