package com.soul360.design.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Shader
import com.soul360.design.data.FurnitureLayer
import com.soul360.design.data.SurfaceLayer
import kotlin.math.floor
import kotlin.math.max

/** Shared geometry so the editor preview and export agree. */
object LayerGeometry {
    fun shadowOffsetY(layer: FurnitureLayer, canvasWidth: Float) = layer.shadow * 0.012f * canvasWidth
    fun shadowAlpha(layer: FurnitureLayer) = 0.55f * layer.shadow
}

object Compositor {
    /** Draws a tiled material texture perspective-mapped into the surface quad. */
    fun drawSurface(canvas: Canvas, s: SurfaceLayer, tex: Bitmap, w: Float, h: Float) {
        if (tex.width <= 0 || tex.height <= 0) return
        val dst = FloatArray(8) { i -> s.pts[i] * if (i % 2 == 0) w else h }
        fun dist(a: Int, b: Int) = kotlin.math.hypot(dst[a * 2] - dst[b * 2], dst[a * 2 + 1] - dst[b * 2 + 1])
        val horiz = (dist(0, 1) + dist(3, 2)).coerceAtLeast(1f)
        val vert = (dist(0, 3) + dist(1, 2)).coerceAtLeast(1f)
        val regionW = s.tiles.coerceAtLeast(0.2f) * tex.width
        val regionH = regionW * (vert / horiz)
        val src = floatArrayOf(0f, 0f, regionW, 0f, regionW, regionH, 0f, regionH)
        val m = Matrix()
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(m) }
            alpha = (s.opacity.coerceIn(0f, 1f) * 255).toInt()
        }
        val path = Path().apply {
            moveTo(dst[0], dst[1]); lineTo(dst[2], dst[3]); lineTo(dst[4], dst[5]); lineTo(dst[6], dst[7]); close()
        }
        canvas.drawPath(path, paint)
    }

    /** Renders room + layers. Output longest edge is capped (default 2400 px) for memory. */
    fun render(
        room: Bitmap,
        layers: List<FurnitureLayer>,
        images: Map<String, Bitmap>,
        shadows: Map<String, Bitmap>,
        maxDimension: Int = 2400,
        surfaces: List<SurfaceLayer> = emptyList(),
    ): Bitmap {
        val f = minOf(1f, maxDimension.toFloat() / max(room.width, room.height))
        val w = floor(room.width * f).toInt().coerceAtLeast(1)
        val h = floor(room.height * f).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(room, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        for (s in surfaces) images[s.imageFile]?.let { drawSurface(canvas, s, it, w.toFloat(), h.toFloat()) }

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
