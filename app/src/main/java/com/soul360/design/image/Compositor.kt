package com.soul360.design.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.soul360.design.data.FurnitureLayer
import kotlin.math.floor
import kotlin.math.max

/** Shared geometry so the editor preview and export agree. */
object LayerGeometry {
    fun shadowOffsetY(layer: FurnitureLayer, canvasWidth: Float) = layer.shadow * 0.012f * canvasWidth
    fun shadowAlpha(layer: FurnitureLayer) = 0.55f * layer.shadow
}

object Compositor {
    /** Renders room + layers. Output longest edge is capped (default 2400 px) for memory. */
    fun render(
        room: Bitmap,
        layers: List<FurnitureLayer>,
        images: Map<String, Bitmap>,
        shadows: Map<String, Bitmap>,
        maxDimension: Int = 2400,
    ): Bitmap {
        val f = minOf(1f, maxDimension.toFloat() / max(room.width, room.height))
        val w = floor(room.width * f).toInt().coerceAtLeast(1)
        val h = floor(room.height * f).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(room, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)

        for (layer in layers) {
            val img = images[layer.imageFile] ?: continue
            val lw = layer.widthFraction * w
            val lh = lw * img.height / img.width
            val cx = layer.centerX * w
            val cy = layer.centerY * h
            val rect = RectF(-lw / 2, -lh / 2, lw / 2, lh / 2)

            val shadow = shadows[layer.imageFile]
            if (shadow != null && layer.shadow > 0.01f) {
                val padX = lw * ImageTools.SHADOW_PAD
                val padY = lh * ImageTools.SHADOW_PAD
                canvas.save()
                canvas.translate(cx, cy + LayerGeometry.shadowOffsetY(layer, w.toFloat()))
                canvas.rotate(layer.rotation)
                if (layer.flipped) canvas.scale(-1f, 1f)
                val sp = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (LayerGeometry.shadowAlpha(layer) * 255).toInt() }
                canvas.drawBitmap(shadow, null, RectF(rect.left - padX, rect.top - padY, rect.right + padX, rect.bottom + padY), sp)
                canvas.restore()
            }

            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(layer.rotation)
            if (layer.flipped) canvas.scale(-1f, 1f)
            val lp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                if (kotlin.math.abs(layer.brightness) > 0.001f) {
                    colorFilter = ColorMatrixColorFilter(ImageTools.brightnessMatrix(layer.brightness))
                }
            }
            canvas.drawBitmap(img, null, rect, lp)
            canvas.restore()
        }
        return out
    }
}
