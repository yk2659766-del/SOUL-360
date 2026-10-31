package com.soul360.design.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One furniture image placed on a room photo. Geometry is relative to the room photo
 * so the screen preview and the full-resolution export match.
 */
data class FurnitureLayer(
    val id: String = UUID.randomUUID().toString(),
    /** PNG file name inside the project folder. */
    val imageFile: String,
    /** Centre as a fraction of room width / height. */
    val centerX: Float = 0.5f,
    val centerY: Float = 0.62f,
    /** Width as a fraction of room width. */
    val widthFraction: Float = 0.35f,
    /** Degrees clockwise. */
    val rotation: Float = 0f,
    val flipped: Boolean = false,
    /** -0.5 .. 0.5 added to RGB. */
    val brightness: Float = 0f,
    /** 0 .. 1 */
    val shadow: Float = 0.4f,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("imageFile", imageFile)
        .put("centerX", centerX.toDouble()).put("centerY", centerY.toDouble())
        .put("widthFraction", widthFraction.toDouble()).put("rotation", rotation.toDouble())
        .put("flipped", flipped).put("brightness", brightness.toDouble()).put("shadow", shadow.toDouble())

    companion object {
        fun fromJson(o: JSONObject) = FurnitureLayer(
            id = o.optString("id", UUID.randomUUID().toString()),
            imageFile = o.getString("imageFile"),
            centerX = o.optDouble("centerX", 0.5).toFloat(),
            centerY = o.optDouble("centerY", 0.62).toFloat(),
            widthFraction = o.optDouble("widthFraction", 0.35).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            flipped = o.optBoolean("flipped", false),
            brightness = o.optDouble("brightness", 0.0).toFloat(),
            shadow = o.optDouble("shadow", 0.4).toFloat(),
        )
    }
}

/**
 * A material (e.g. an EGGER decor or a tile) laid onto a surface of the room photo.
 * `pts` are the 4 corners TL, TR, BR, BL as fractions of room width/height (8 floats);
 * the texture is perspective-mapped and tiled inside that quad.
 */
data class SurfaceLayer(
    val id: String = UUID.randomUUID().toString(),
    val imageFile: String,
    val name: String = "",
    val surface: String = "Floor",
    val pts: List<Float>,
    /** How many times the texture repeats across the top edge. */
    val tiles: Float = 4f,
    val opacity: Float = 0.92f,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("imageFile", imageFile).put("name", name).put("surface", surface)
        .put("pts", JSONArray().apply { pts.forEach { put(it.toDouble()) } })
        .put("tiles", tiles.toDouble()).put("opacity", opacity.toDouble())

    companion object {
        val SURFACES = listOf("Floor", "Wall", "Cabinets", "Worktop", "Ceiling", "Door")

        fun defaultPts(surface: String): List<Float> = when (surface) {
            "Wall" -> listOf(0.2f, 0.15f, 0.8f, 0.15f, 0.8f, 0.6f, 0.2f, 0.6f)
            "Cabinets" -> listOf(0.3f, 0.4f, 0.7f, 0.4f, 0.7f, 0.75f, 0.3f, 0.75f)
            "Worktop" -> listOf(0.25f, 0.5f, 0.75f, 0.5f, 0.8f, 0.57f, 0.2f, 0.57f)
            "Ceiling" -> listOf(0.0f, 0.0f, 1.0f, 0.0f, 0.78f, 0.14f, 0.22f, 0.14f)
            "Door" -> listOf(0.42f, 0.28f, 0.58f, 0.28f, 0.58f, 0.78f, 0.42f, 0.78f)
            else -> listOf(0.18f, 0.64f, 0.82f, 0.64f, 1.0f, 1.0f, 0.0f, 1.0f)
        }

        fun fromJson(o: JSONObject): SurfaceLayer {
            val arr = o.optJSONArray("pts")
            val pts = if (arr != null && arr.length() == 8) (0 until 8).map { arr.getDouble(it).toFloat() }
            else defaultPts(o.optString("surface", "Floor"))
            return SurfaceLayer(
                id = o.optString("id", UUID.randomUUID().toString()),
                imageFile = o.getString("imageFile"),
                name = o.optString("name", ""),
                surface = o.optString("surface", "Floor"),
                pts = pts,
                tiles = o.optDouble("tiles", 4.0).toFloat(),
                opacity = o.optDouble("opacity", 0.92).toFloat(),
            )
        }
    }
}

data class PhotoProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val created: Long = System.currentTimeMillis(),
    val modified: Long = System.currentTimeMillis(),
    val roomImageFile: String = "room.jpg",
    val layers: List<FurnitureLayer> = emptyList(),
    val surfaces: List<SurfaceLayer> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("created", created).put("modified", modified)
        .put("roomImageFile", roomImageFile)
        .put("layers", JSONArray().apply { layers.forEach { put(it.toJson()) } })
        .put("surfaces", JSONArray().apply { surfaces.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): PhotoProject {
            val arr = o.optJSONArray("layers") ?: JSONArray()
            val sArr = o.optJSONArray("surfaces") ?: JSONArray()
            return PhotoProject(
                id = o.getString("id"),
                name = o.optString("name", "Room"),
                created = o.optLong("created"),
                modified = o.optLong("modified"),
                roomImageFile = o.optString("roomImageFile", "room.jpg"),
                layers = (0 until arr.length()).map { FurnitureLayer.fromJson(arr.getJSONObject(it)) },
                surfaces = (0 until sArr.length()).map { SurfaceLayer.fromJson(sArr.getJSONObject(it)) },
            )
        }
    }
}
