@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.soul360.design.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.soul360.design.data.SurfaceLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** A supplier website that can be browsed inside the app. */
data class CatalogSite(val name: String, val url: String, val hint: String)

val CATALOG_SITES = listOf(
    CatalogSite("Sanipex", "https://sanipexgroup.com/", "Bathrooms, kitchens, tiles, lighting"),
    CatalogSite("EGGER", "https://www.egger.com/en/furniture-interior-design/decorative-collection/?country=AE", "Decors & boards"),
)

/** What the user picked: an image plus how to use it. */
sealed interface CatalogPick {
    val image: Bitmap
    val source: String
    data class Item(override val image: Bitmap, override val source: String) : CatalogPick
    data class Material(override val image: Bitmap, override val source: String, val surface: String) : CatalogPick
}

/**
 * In-app browser for supplier catalogues (Sanipex, EGGER, or any site).
 * Long-press a product or decor image to add it to the design.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun CatalogScreen(onPick: (CatalogPick) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var siteIdx by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Pair<Bitmap, String>?>(null) }
    var loadingImage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showHint by remember { mutableStateOf(true) }
    var surface by remember { mutableStateOf("Floor") }

    BackHandler {
        when {
            picked != null -> picked = null
            canGoBack -> webView?.goBack()
            else -> onClose()
        }
    }

    fun openSite(i: Int) {
        siteIdx = i
        if (i < CATALOG_SITES.size) webView?.loadUrl(CATALOG_SITES[i].url)
    }

    fun fetchImage(url: String, pageUrl: String, userAgent: String) {
        if (url.isBlank()) { error = "No image found there. Long-press directly on a product or decor picture."; return }
        loadingImage = true
        scope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { downloadImage(url, pageUrl, userAgent) }
                picked = bmp to (sourceName(pageUrl))
            } catch (e: Exception) {
                error = "Couldn't load that image (${e.message}). Try opening the product page and long-pressing the large picture."
            } finally { loadingImage = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Cream).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close catalogue", tint = Palette.Ink) }
            Text("Catalogue", style = MaterialTheme.typography.titleLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
            IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (canGoBack) Palette.Ink else Palette.Stone)
            }
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Filled.Refresh, "Reload", tint = Palette.Ink) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CATALOG_SITES.forEachIndexed { i, site ->
                FilterChip(selected = siteIdx == i, onClick = { openSite(i) }, label = { Text(site.name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Orange, selectedLabelColor = Color.White))
            }
            FilterChip(selected = siteIdx == CATALOG_SITES.size, onClick = { siteIdx = CATALOG_SITES.size },
                label = { Text("Other website") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Orange, selectedLabelColor = Color.White))
        }
        if (siteIdx == CATALOG_SITES.size) {
            OutlinedTextField(customUrl, { customUrl = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true, label = { Text("Website address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val u = customUrl.trim()
                    if (u.isNotEmpty()) webView?.loadUrl(if (u.startsWith("http")) u else "https://$u")
                }))
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth().padding(top = 4.dp), color = Palette.Orange)
        else Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme ?: ""
                                return scheme != "http" && scheme != "https"   // ignore tel:, mailto:, intent: etc.
                            }
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        }
                        var lastX = 0f
                        var lastY = 0f
                        setOnTouchListener { _, ev ->
                            if (ev.action == MotionEvent.ACTION_DOWN) { lastX = ev.x; lastY = ev.y }
                            false
                        }
                        setOnLongClickListener { v ->
                            val wv = v as WebView
                            val hit = wv.hitTestResult
                            val page = wv.url ?: ""
                            val ua = wv.settings.userAgentString
                            if ((hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && !hit.extra.isNullOrBlank()) {
                                fetchImage(hit.extra!!, page, ua)
                            } else {
                                @Suppress("DEPRECATION") val scale = wv.scale
                                val js = FIND_IMAGE_JS.replace("__X__", (lastX / scale).toString()).replace("__Y__", (lastY / scale).toString())
                                wv.evaluateJavascript(js) { result ->
                                    val url = runCatching { JSONArray("[$result]").getString(0) }.getOrDefault("")
                                    fetchImage(url, page, ua)
                                }
                            }
                            true
                        }
                        loadUrl(CATALOG_SITES[0].url)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )

            if (showHint) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(RoundedCornerShape(20.dp))
                        .background(Palette.Ink.copy(alpha = 0.88f)).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.TouchApp, null, tint = Palette.Orange)
                    Text("Long-press any product or material picture to add it to your photo",
                        Modifier.weight(1f).padding(horizontal = 10.dp), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { showHint = false }) { Icon(Icons.Filled.Close, "Hide tip", tint = Color.White) }
                }
            }
            if (loadingImage) {
                Box(Modifier.fillMaxSize().background(Palette.Cream.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { LogoLoader() }
            }
        }
    }

    // Pick sheet
    picked?.let { (bmp, source) ->
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { picked = null }, contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Palette.Cream)
                    .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Add from $source", style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
                Image(bmp.asImageBitmap(), "Selected image", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(MaterialTheme.shapes.medium).background(Color.White))
                Button(onClick = { onPick(CatalogPick.Item(bmp, source)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Place as an item (product / furniture)")
                }
                Text("Or use it as a material on a surface:", style = MaterialTheme.typography.labelLarge, color = Palette.Brown)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SurfaceLayer.SURFACES.forEach { s ->
                        FilterChip(selected = surface == s, onClick = { surface = s }, label = { Text(s) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Brown, selectedLabelColor = Color.White))
                    }
                }
                OutlinedButton(onClick = { onPick(CatalogPick.Material(bmp, source, surface)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Apply as material on $surface")
                }
                Text("Tip: \"Remove background\" works on items after placing them.", style = MaterialTheme.typography.labelSmall, color = Palette.Taupe)
            }
        }
    }

    error?.let { MessageDialog("Catalogue", it) { error = null } }
}

private fun sourceName(pageUrl: String): String {
    val host = runCatching { URL(pageUrl).host.removePrefix("www.") }.getOrDefault("")
    return when {
        host.contains("sanipex") -> "Sanipex"
        host.contains("egger") -> "EGGER"
        host.isNotEmpty() -> host
        else -> "website"
    }
}

/** Finds the image under a point: <img>, <picture>, or a CSS background image. */
private const val FIND_IMAGE_JS = """
(function(x,y){
  function bg(el){ try { var b=getComputedStyle(el).backgroundImage; var m=b&&b.match(/url\(["']?([^"')]+)["']?\)/); return m?m[1]:''; } catch(e){ return ''; } }
  function abs(u){ try { return new URL(u, location.href).href; } catch(e){ return u||''; } }
  var el=document.elementFromPoint(x,y);
  for(var i=0; el && i<7; i++, el=el.parentElement){
    if(el.tagName==='IMG') return abs(el.currentSrc||el.src);
    var b=bg(el); if(b) return abs(b);
    if(i>0 && el.querySelector){ var im=el.querySelector('img'); if(im && (im.currentSrc||im.src)) return abs(im.currentSrc||im.src); }
  }
  return '';
})(__X__,__Y__)
"""

private fun downloadImage(url: String, pageUrl: String, userAgent: String): Bitmap {
    val bytes: ByteArray = if (url.startsWith("data:")) {
        Base64.decode(url.substringAfter(","), Base64.DEFAULT)
    } else {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", pageUrl)
            setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8")
            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        conn.inputStream.use { it.readBytes() }
    }
    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        ?: throw IllegalStateException("unsupported image format")
    if (bmp.width < 40 || bmp.height < 40) throw IllegalStateException("image is too small")
    return bmp
}
