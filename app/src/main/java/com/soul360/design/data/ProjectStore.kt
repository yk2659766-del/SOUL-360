package com.soul360.design.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.soul360.design.image.ImageTools
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device storage (app-private):
 *   files/projects/<id>/ project.json, room.jpg, thumb.jpg, layer PNGs
 *   files/rooms/ (.glb files)      – 3D room scans imported from a scanning app
 *   files/models/ (.glb files)     – 3D furniture models (imported or AI-generated)
 */
class ProjectStore(private val context: Context) {
    val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
    val roomsDir = File(context.filesDir, "rooms").apply { mkdirs() }
    val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    fun listProjects(): List<PhotoProject> =
        (projectsDir.listFiles() ?: emptyArray())
            .mapNotNull { dir ->
                runCatching { PhotoProject.fromJson(JSONObject(File(dir, "project.json").readText())) }.getOrNull()
            }
            .sortedByDescending { it.modified }

    fun listRooms(): List<File> = glbFiles(roomsDir)
    fun listModels(): List<File> = glbFiles(modelsDir)

    private fun glbFiles(dir: File) = (dir.listFiles() ?: emptyArray())
        .filter { it.extension.equals("glb", ignoreCase = true) }
        .sortedByDescending { it.lastModified() }

    fun folder(project: PhotoProject) = File(projectsDir, project.id)

    fun createProject(room: Bitmap): PhotoProject {
        val name = "Room – " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val project = PhotoProject(name = name)
        val dir = folder(project).apply { mkdirs() }
        val scaled = ImageTools.downscale(room, 2400)
        File(dir, project.roomImageFile).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        save(project, thumbnail = scaled)
        return project
    }

    fun save(project: PhotoProject, thumbnail: Bitmap? = null) {
        val dir = folder(project).apply { mkdirs() }
        val tmp = File(dir, "project.json.tmp")
        tmp.writeText(project.toJson().toString(2))
        if (!tmp.renameTo(File(dir, "project.json"))) {
            File(dir, "project.json").writeText(project.toJson().toString(2))
            tmp.delete()
        }
        thumbnail?.let { t ->
            File(dir, "thumb.jpg").outputStream().use { ImageTools.downscale(t, 600).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
    }

    fun delete(project: PhotoProject) { folder(project).deleteRecursively() }

    fun roomImage(project: PhotoProject): Bitmap? = decode(File(folder(project), project.roomImageFile))
    fun thumbnail(project: PhotoProject): Bitmap? = decode(File(folder(project), "thumb.jpg"))
    fun layerImage(project: PhotoProject, name: String): Bitmap? = decode(File(folder(project), name))

    private fun decode(file: File): Bitmap? =
        if (file.exists()) BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) else null

    /** Writes a furniture image as PNG (keeps transparency) and returns its file name. */
    fun writeLayerImage(project: PhotoProject, image: Bitmap): String {
        val name = "layer-${java.util.UUID.randomUUID()}.png"
        File(folder(project), name).outputStream().use {
            ImageTools.downscale(image, 2048).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return name
    }

    fun pruneUnusedLayers(project: PhotoProject) {
        val used = project.layers.map { it.imageFile }.toSet()
        folder(project).listFiles()?.forEach { f ->
            if (f.name.startsWith("layer-") && f.name !in used) f.delete()
        }
    }

    /** Copies a GLB picked by the user into the app library. */
    fun importGlb(uri: Uri, intoRooms: Boolean): File {
        val dir = if (intoRooms) roomsDir else modelsDir
        val display = displayName(uri)?.substringBeforeLast('.') ?: if (intoRooms) "Room" else "Model"
        val dest = uniqueFile(dir, display)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected file." }
            dest.outputStream().use { input.copyTo(it) }
        }
        val header = dest.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        if (String(header, Charsets.US_ASCII) != "glTF") {
            dest.delete()
            throw IllegalArgumentException("That file is not a binary glTF (.glb) model.")
        }
        return dest
    }

    fun saveModel(bytes: ByteArray, name: String): File =
        uniqueFile(modelsDir, name).apply { writeBytes(bytes) }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun uniqueFile(dir: File, base: String): File {
        val safe = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").ifBlank { "Model" }
        var f = File(dir, "$safe.glb")
        var i = 2
        while (f.exists()) { f = File(dir, "$safe $i.glb"); i++ }
        return f
    }
}
