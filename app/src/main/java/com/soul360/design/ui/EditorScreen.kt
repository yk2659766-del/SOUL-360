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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private sealed interface AiState {
    data class ConfirmModel(val layer: FurnitureLayer) : AiState
    data class Running(val title: String, val status: String) : AiState
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
    var renderInput by remember { mutableStateOf<Pair<Bitmap, List<Bitmap>>?>(null) }
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

    fun openRender() {
        val r = room ?: return
        busy = "Preparing your design…"
        scope.launch {
            try {
                val layers = project.layers
                val (comp, furn) = withContext(Dispatchers.Default) {
                    val c = Compositor.render(r, layers, images, shadows, 1536)
                    val f = layers.map { it.imageFile }.distinct().mapNotNull { images[it] }
                    c to f
                }
                renderInput = comp to furn
            } catch (e: Exception) {
                message = "AI Render" to (e.message ?: "Could not prepare the image.")
            } finally { busy = null }
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

    Column(Modifier.fillMaxSize().background(Palette.Ink).statusBarsPadding().navigationBarsPadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close and save", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(project.name.removePrefix("Room – "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("${project.layers.size} item(s)", style = MaterialTheme.typography.labelSmall, color = Palette.Taupe)
            }
            IconButton(onClick = {
                persist(reportErrors = true)
                if (message == null) android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Filled.Save, "Save", tint = Color.White) }
            Box {
                IconButton(onClick = { exportMenu = true }) { Icon(Icons.Filled.IosShare, "Export", tint = Color.White) }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    DropdownMenuItem(text = { Text("Share image…") }, onClick = { exportMenu = false; exportImage(false) })
                    DropdownMenuItem(text = { Text("Save to gallery") }, onClick = { exportMenu = false; exportImage(true) })
                }
            }
        }

        // Canvas
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            val r = room
            if (r == null) {
                CircularProgressIndicator(color = Palette.Orange)
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
                        .clip(RoundedCornerShape(18.dp))
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
                    if (showOriginal) {
                        Text("ORIGINAL", Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (project.layers.isEmpty()) {
                    Text("Tap  Add  to place furniture images, then  ✦ Render",
                        Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Selected-layer controls
        if (selected != null && !showOriginal) {
            Surface(color = Palette.Cream, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(horizontal = 10.dp)) {
                Column(Modifier.heightIn(max = 270.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected item", style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedId = null }) { Text("Done") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { cutout(selected) },
                            label = { Text("Remove background") }, leadingIcon = { Icon(Icons.Filled.ContentCut, null) })
                        AssistChip(onClick = { update(selected.id) { it.copy(flipped = !it.flipped) } },
                            label = { Text("Flip") }, leadingIcon = { Icon(Icons.Filled.Flip, null) })
                        AssistChip(onClick = {
                            project = project.copy(layers = project.layers.filter { it.id != selected.id } + selected)
                        }, label = { Text("To front") }, leadingIcon = { Icon(Icons.Filled.FlipToFront, null) })
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

        // Floating toolbar
        Row(
            Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Palette.Charcoal).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ToolButton(Icons.Filled.AddPhotoAlternate, "Add") {
                pickFurniture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ToolButton(Icons.Filled.Search, "Find") { showSearch = true }
            ToolButton(Icons.Filled.Compare, "Original", active = showOriginal) { showOriginal = !showOriginal }
            Row(
                Modifier.clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Palette.Orange, Palette.OrangeDeep)))
                    .clickable { openRender() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                HSpace(8)
                Text("Render", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    busy?.let { text ->
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
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
        null -> {}
    }

    renderInput?.let { (comp, furn) ->
        RenderScreen(settings, comp, furn, onClose = { renderInput = null })
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (active) Palette.Orange.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Palette.Stone, style = MaterialTheme.typography.labelSmall)
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
