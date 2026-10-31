package com.soul360.design.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

object ImageTools {

    /** Decodes a content Uri, applies EXIF rotation, and caps the longest edge. */
    fun decode(context: Context, uri: Uri, maxDimension: Int = 2400): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file is not a supported image." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("The image could not be decoded.")
        val orientation = runCatching {
            resolver.openInputStream(uri).use { ExifInterface(it!!).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        val upright = if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        return downscale(upright, maxDimension)
    }

    fun downscale(src: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDimension) return src
        val f = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt().coerceAtLeast(1),
            (src.height * f).roundToInt().coerceAtLeast(1), true)
    }

    /** Crops away fully transparent borders. */
    fun trimTransparent(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 8) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return src
        return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /** Colour matrix that adds `brightness` (-0.5..0.5) to RGB; same as the on-screen preview. */
    fun brightnessMatrix(brightness: Float): FloatArray {
        val o = brightness * 255f
        return floatArrayOf(
            1f, 0f, 0f, 0f, o,
            0f, 1f, 0f, 0f, o,
            0f, 0f, 1f, 0f, o,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /**
     * Soft black silhouette of a cutout, used for the drop shadow on screen and in export.
     * Built by shrinking a black-filled copy and drawing it back up with filtering, padded so
     * the blur can spread past the edges. Returned bitmap is `(1 + 2*PAD)` times the size.
     */
    const val SHADOW_PAD = 0.12f

    fun shadowSilhouette(src: Bitmap): Bitmap {
        val small = 48
        val f = small.toFloat() / max(src.width, src.height)
        val sw = (src.width * f).roundToInt().coerceAtLeast(2)
        val sh = (src.height * f).roundToInt().coerceAtLeast(2)
        val padW = (sw * SHADOW_PAD).roundToInt()
        val padH = (sh * SHADOW_PAD).roundToInt()
        val out = Bitmap.createBitmap(sw + padW * 2, sh + padH * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
        }
        c.drawBitmap(Bitmap.createScaledBitmap(src, sw, sh, true), padW.toFloat(), padH.toFloat(), p)
        // Two cheap box-blur passes by down/up scaling.
        var b = out
        repeat(2) {
            val tiny = Bitmap.createScaledBitmap(b, (b.width / 3).coerceAtLeast(2), (b.height / 3).coerceAtLeast(2), true)
            b = Bitmap.createScaledBitmap(tiny, out.width, out.height, true)
        }
        return b
    }
}
