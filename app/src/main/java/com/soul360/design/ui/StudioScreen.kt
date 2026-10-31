@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.soul360.design.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.soul360.design.data.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val ORIGIN = "https://appassets.androidplatform.net"

/**
 * 3D Studio: orbit a GLB room scan, place one GLB furniture model with X/Y/Z (metres),
 * scale and rotation, and export the furnished scene as GLB. Rendering is done by a bundled
 * three.js page (assets/studio) inside a WebView; files are served from app storage.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StudioScreen(store: ProjectStore, initialRoom: File?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val rooms = remember(refresh) { store.listRooms() }
    val models = remember(refresh) { store.listModels() }

    var room by remember { mutableStateOf(initialRoom) }
    var model by remember { mutableStateOf<File?>(null) }
    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Starting 3D view…") }
    var error by remember { mutableStateOf<String?>(null) }
    var hasFurniture by remember { mutableStateOf(false) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var z by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rot by remember { mutableFloatStateOf(0f) }
    var roomMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun js(code: String) { webView?.evaluateJavascript(code, null) }
    fun fileUrl(f: File): String {
        val rel = f.relativeTo(context.filesDir).invariantSeparatorsPath
        return "$ORIGIN/files/" + rel.split('/').joinToString("/") { Uri.encode(it) }
    }
    fun pushTransform() = js(String.format(Locale.US, "S360.setTransform(%f,%f,%f,%f,%f)", x, y, z, scale, rot))
    fun loadRoom(f: File) { room = f; js("S360.loadRoom(${JSONObject.quote(fileUrl(f))})") }
    fun loadModel(f: File) { model = f; js("S360.loadFurniture(${JSONObject.quote(fileUrl(f))})") }

    val bridge = remember {
        val main = Handler(Looper.getMainLooper())
        object {
            @JavascriptInterface
            fun onStatus(msg: String) {
                main.post {
                    if (msg == "ready") { ready = true; status = ""; room?.let { loadRoom(it) } } else { status = msg }
                }
            }

            @JavascriptInterface
            fun onError(msg: String) { main.post { status = ""; error = msg } }

            @JavascriptInterface
            fun onRoomLoaded(json: String) { /* room size available in json if needed */ }

            @JavascriptInterface
            fun onFurnitureLoaded(json: String) {
                main.post {
                    val o = JSONObject(json)
                    x = o.getDouble("x").toFloat(); y = o.getDouble("y").toFloat(); z = o.getDouble("z").toFloat()
                    scale = o.getDouble("scale").toFloat(); rot = 0f; hasFurniture = true
                }
            }

            @JavascriptInterface
            fun onExport(b64: String) {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val file = exportFile(context, "Soul360-Scene-${timestamp()}.glb").apply { writeBytes(bytes) }
                main.post { shareFile(context, file, "model/gltf-binary") }
            }
        }
    }

    val importRoom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { val f = withContext(Dispatchers.IO) { store.importGlb(uri, true) }; refresh++; loadRoom(f) }
            catch (e: Exception) { error = e.message }
        }
    }
    val importModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { val f = withContext(Dispatchers.IO) { store.importGlb(uri, false) }; refresh++; loadModel(f) }
            catch (e: Exception) { error = e.message }
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Cream).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            LogoMark(Modifier.height(26.dp).padding(end = 8.dp))
            Text("3D Studio", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { js("S360.resetView()") }) { Icon(Icons.Filled.CenterFocusStrong, "Reset view") }
            IconButton(onClick = { js("S360.exportGLB()") }, enabled = room != null || hasFurniture) {
                Icon(Icons.Filled.IosShare, "Export scene")
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val loader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                        .addPathHandler("/files/", WebViewAssetLoader.InternalStoragePathHandler(ctx, ctx.filesDir))
                        .build()
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                loader.shouldInterceptRequest(request.url)
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                                !request.url.toString().startsWith(ORIGIN)
                        }
                        addJavascriptInterface(bridge, "Android")
                        loadUrl("$ORIGIN/assets/studio/index.html")
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
            if (status.isNotEmpty() || (ready && room == null && !hasFurniture)) {
                Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = MaterialTheme.shapes.large, color = Palette.Sand) {
                    Text(
                        status.ifEmpty { "Choose or import a room scan (.glb) below. Scans come from a 3D scanning app that exports GLB." },
                        Modifier.padding(16.dp),
                    )
                }
            }
        }

        Surface(color = Palette.Sand, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(8.dp)) {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        AssistChip(onClick = { roomMenu = true }, enabled = ready,
                            label = { Text(room?.nameWithoutExtension ?: "Room") }, leadingIcon = { Icon(Icons.Filled.Home, null) })
                        DropdownMenu(roomMenu, { roomMenu = false }) {
                            rooms.forEach { f -> DropdownMenuItem(text = { Text(f.nameWithoutExtension) }, onClick = { roomMenu = false; loadRoom(f) }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Import room .glb…") }, onClick = { roomMenu = false; importRoom.launch(arrayOf("*/*")) })
                        }
                    }
                    Box {
                        AssistChip(onClick = { modelMenu = true }, enabled = ready,
                            label = { Text(model?.nameWithoutExtension ?: "Add furniture") }, leadingIcon = { Icon(Icons.Filled.Chair, null) })
                        DropdownMenu(modelMenu, { modelMenu = false }) {
                            models.forEach { f -> DropdownMenuItem(text = { Text(f.nameWithoutExtension) }, onClick = { modelMenu = false; loadModel(f) }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Import furniture .glb…") }, onClick = { modelMenu = false; importModel.launch(arrayOf("*/*")) })
                        }
                    }
                }
                if (hasFurniture) {
                    LabeledSlider("X (m)", x, -10f..10f, String.format(Locale.US, "%.2f", x)) { x = it; pushTransform() }
                    LabeledSlider("Y (m)", y, -3f..5f, String.format(Locale.US, "%.2f", y)) { y = it; pushTransform() }
                    LabeledSlider("Z (m)", z, -10f..10f, String.format(Locale.US, "%.2f", z)) { z = it; pushTransform() }
                    LabeledSlider("Scale", scale, 0.01f..5f, String.format(Locale.US, "%.2f×", scale)) { scale = it; pushTransform() }
                    LabeledSlider("Rotation", rot, -180f..180f, "${rot.roundToInt()}°") { rot = it; pushTransform() }
                    Text("Export the scene before leaving — the 3D arrangement is not saved.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
                } else {
                    Text("Furniture models come from \"Make 3D\" in the photo editor or any GLB file.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
                }
            }
        }
    }

    error?.let { MessageDialog("3D Studio", it) { error = null } }
}
