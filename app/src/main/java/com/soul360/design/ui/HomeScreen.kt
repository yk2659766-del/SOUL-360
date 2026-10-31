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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.soul360.design.R
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
                val project = withContext(Dispatchers.IO) {
                    store.createProject(ImageTools.decode(context, uri, 2400))
                }
                onOpenProject(project)
            } catch (e: Exception) {
                error = e.message ?: "The photo could not be opened."
            } finally { busy = false }
        }
    }

    val pickRoom = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) createFrom(uri)
    }
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

    Column(
        Modifier.fillMaxSize().background(Palette.Cream).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_mark), contentDescription = "SOUL 360 DESIGN logo", modifier = Modifier.size(56.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("SOUL 360", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.Ink, letterSpacing = 3.sp)
                Text("DESIGN", fontSize = 13.sp, color = Palette.Orange, letterSpacing = 6.sp)
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Palette.Ink) }
        }

        Column(Modifier.screenPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                val file = File(dir, "capture-${timestamp()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri = uri.toString()
                try { takePicture.launch(uri) } catch (e: ActivityNotFoundException) { error = "No camera app is available on this device." }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.CameraAlt, null); HSpace(); Text("Photograph a room")
            }
            FilledTonalButton(onClick = {
                pickRoom.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.PhotoLibrary, null); HSpace(); Text("Choose a room photo")
            }
            FilledTonalButton(onClick = { onOpenStudio(rooms.firstOrNull()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.ViewInAr, null); HSpace(); Text("3D Studio")
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
            item { SectionTitle("Photo projects") }
            if (projects.isEmpty()) item { EmptyHint("Photograph or choose a room to start designing.") }
            items(projects, key = { it.id }) { p ->
                ProjectRow(store, p, onClick = { onOpenProject(p) }, onDelete = { pendingDelete = p })
            }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("3D room scans", Modifier.weight(1f))
                    TextButton(onClick = { importRoom.launch(arrayOf("*/*")) }) { Text("Import .glb") }
                }
            }
            if (rooms.isEmpty()) item {
                EmptyHint("Scan a room with a 3D scanning app that exports GLB, then import it here.")
            }
            items(rooms, key = { it.path }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenStudio(f) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ViewInAr, null, tint = Palette.Orange)
                    Text(f.nameWithoutExtension, Modifier.weight(1f).padding(start = 12.dp))
                    IconButton(onClick = { store.roomsDir.resolve(f.name).delete(); refresh++ }) {
                        Icon(Icons.Filled.Delete, "Delete scan", tint = Palette.Taupe)
                    }
                }
            }
        }
    }

    if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    error?.let { MessageDialog("Something went wrong", it) { error = null } }
    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete project?") },
            text = { Text("\"${p.name}\" and its furniture images will be removed from this device.") },
            confirmButton = { TextButton(onClick = { store.delete(p); pendingDelete = null; refresh++ }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = Palette.Ink)

@Composable
private fun EmptyHint(text: String) =
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Palette.Taupe, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun ProjectRow(store: ProjectStore, p: PhotoProject, onClick: () -> Unit, onDelete: () -> Unit) {
    var thumb by remember(p.id, p.modified) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(p.id, p.modified) { thumb = withContext(Dispatchers.IO) { store.thumbnail(p) } }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 84.dp, height = 60.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Sand)) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge)
            Text("${p.layers.size} item(s) · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.modified)),
                style = MaterialTheme.typography.bodySmall, color = Palette.Taupe)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete project", tint = Palette.Taupe) }
        Spacer(Modifier.size(0.dp))
    }
}
