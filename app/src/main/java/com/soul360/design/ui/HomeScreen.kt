@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.soul360.design.ui

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.soul360.design.data.PhotoProject
import com.soul360.design.data.ProjectStore
import com.soul360.design.image.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    store: ProjectStore,
    onOpenProject: (PhotoProject) -> Unit,
    onOpenStudio: (File?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val projects = remember(refresh) { store.listProjects() }
    val rooms = remember(refresh) { store.listRooms() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<PhotoProject?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun createFrom(uri: Uri) {
        busy = true
        scope.launch {
            try {
                val project = withContext(Dispatchers.IO) { store.createProject(ImageTools.decode(context, uri, 2400)) }
                onOpenProject(project)
            } catch (e: Exception) {
                error = e.message ?: "The photo could not be opened."
            } finally { busy = false }
        }
    }

    val pickRoom = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) createFrom(uri) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) createFrom(Uri.parse(u))
    }
    val importRoom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val f = withContext(Dispatchers.IO) { store.importGlb(uri, intoRooms = true) }
                refresh++
                onOpenStudio(f)
            } catch (e: Exception) { error = e.message }
        }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture-${timestamp()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri.toString()
        try { takePicture.launch(uri) } catch (e: ActivityNotFoundException) { error = "No camera app is available on this device." }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(Palette.Cream),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hero
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .background(Brush.verticalGradient(listOf(Palette.Cream, Palette.Sand)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Palette.Brown) }
                    }
                    FullLogo(markHeight = 92.dp, wordSize = 24.sp, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
                    Spacer(Modifier.height(22.dp))
                    Text("Design your space, beautifully.", color = Palette.Ink, fontFamily = FontFamily.Serif,
                        fontSize = 25.sp, lineHeight = 31.sp, modifier = Modifier.padding(end = 8.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Place furniture in your room, then let AI turn it into a photoreal render.",
                        color = Palette.Brown, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroAction(Icons.Filled.CameraAlt, "Photograph\nyour room", primary = true, modifier = Modifier.weight(1f)) { launchCamera() }
                        HeroAction(Icons.Filled.PhotoLibrary, "Choose\nfrom gallery", primary = false, modifier = Modifier.weight(1f)) {
                            pickRoom.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                }
            }
        }

        // AI feature banner
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(Brush.horizontalGradient(listOf(Palette.Ink, Palette.Charcoal)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Palette.Orange), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("AI Render with ChatGPT or Gemini", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("Open a design and tap  ✦ Render", style = MaterialTheme.typography.bodySmall, color = Palette.Stone)
                }
            }
        }

        // 3D studio tile
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(Color.White).clickable { onOpenStudio(rooms.firstOrNull()) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ViewInAr, null, tint = Palette.Orange, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("3D Studio", style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                    Text(if (rooms.isEmpty()) "Import a room scan (.glb)" else "${rooms.size} room scan(s)",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
                }
                TextButton(onClick = { importRoom.launch(arrayOf("*/*")) }) { Text("Import") }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Palette.Taupe)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text("Your designs", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink, modifier = Modifier.weight(1f))
                if (projects.isNotEmpty()) Text("${projects.size}", color = Palette.Taupe)
            }
        }
        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No designs yet. Photograph or choose a room above to begin.",
                    Modifier.padding(horizontal = 20.dp), color = Palette.Taupe, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(projects, key = { it.id }) { p ->
            val index = projects.indexOf(p)
            ProjectCard(
                store, p,
                modifier = Modifier.animateItem().padding(start = if (index % 2 == 0) 16.dp else 0.dp, end = if (index % 2 == 1) 16.dp else 0.dp),
                onClick = { onOpenProject(p) },
                onDelete = { pendingDelete = p },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.navigationBarsPadding()) }
    }

    if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(MaterialTheme.shapes.large).background(Palette.Cream).padding(22.dp)) { LogoLoader() }
    }
    error?.let { MessageDialog("Something went wrong", it) { error = null } }
    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete design?") },
            text = { Text("\"${p.name}\" and its furniture images will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { store.delete(p); pendingDelete = null; refresh++ }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HeroAction(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(if (primary) Palette.Orange else Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(icon, null, tint = if (primary) Color.White else Palette.Orange)
        Spacer(Modifier.height(18.dp))
        Text(label, color = if (primary) Color.White else Palette.Ink, style = MaterialTheme.typography.labelLarge, lineHeight = 18.sp)
    }
}

@Composable
private fun ProjectCard(store: ProjectStore, p: PhotoProject, modifier: Modifier, onClick: () -> Unit, onDelete: () -> Unit) {
    var thumb by remember(p.id, p.modified) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(p.id, p.modified) { thumb = withContext(Dispatchers.IO) { store.thumbnail(p) } }
    Box(modifier.fillMaxWidth().aspectRatio(0.8f).clip(MaterialTheme.shapes.medium).background(Palette.Sand).clickable(onClick = onClick)) {
        thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(p.name.removePrefix("Room – "), color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.layers.size} item(s) · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.modified)),
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Delete design", tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}
