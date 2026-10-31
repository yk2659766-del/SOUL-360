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

data class PhotoProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val created: Long = System.currentTimeMillis(),
    val modified: Long = System.currentTimeMillis(),
    val roomImageFile: String = "room.jpg",
    val layers: List<FurnitureLayer> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("created", created).put("modified", modified)
        .put("roomImageFile", roomImageFile)
        .put("layers", JSONArray().apply { layers.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): PhotoProject {
            val arr = o.optJSONArray("layers") ?: JSONArray()
            return PhotoProject(
                id = o.getString("id"),
                name = o.optString("name", "Room"),
                created = o.optLong("created"),
                modified = o.optLong("modified"),
                roomImageFile = o.optString("roomImageFile", "room.jpg"),
                layers = (0 until arr.length()).map { FurnitureLayer.fromJson(arr.getJSONObject(it)) },
            )
        }
    }
}
