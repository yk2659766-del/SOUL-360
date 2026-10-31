package com.soul360.design.image

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device furniture cutout with ML Kit Subject Segmentation.
 * The model is delivered by Google Play services; the first run may need a moment
 * (and a network connection) to download it.
 */
object BackgroundRemover {
    private val segmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
    }

    suspend fun removeBackground(src: Bitmap): Bitmap {
        val input = InputImage.fromBitmap(src, 0)
        val result = try {
            segmenter.process(input).await()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Background removal is not ready (${e.message}). Make sure Google Play services is up to date and you are online for the first use.", e
            )
        }
        val fg = result.foregroundBitmap
            ?: throw IllegalStateException("No clear furniture subject was found. Try a photo with one item on a simple background.")
        return withContext(Dispatchers.Default) { ImageTools.trimTransparent(fg) }
    }
}
