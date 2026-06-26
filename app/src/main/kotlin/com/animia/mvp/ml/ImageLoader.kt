package com.animia.mvp.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Décode une image (caméra pleine résolution ou galerie) en un Bitmap propre pour la
 * classification : sous-échantillonné pour la mémoire, orienté correctement (EXIF),
 * et mis à l'échelle à [MAX_DIM] px sur le plus grand côté.
 *
 * La capture précédente (`TakePicturePreview`) ne renvoyait qu'une vignette ~96-256 px,
 * d'où la mauvaise reconnaissance. Ici on part de la vraie photo.
 */
object ImageLoader {

    private const val TAG = "ImageLoader"
    private const val MAX_DIM = 512

    fun load(context: Context, uri: Uri, maxDim: Int = MAX_DIM): Bitmap? = runCatching {
        val cr = context.contentResolver

        // 1. Lire les dimensions sans charger l'image
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2. Sous-échantillonnage (puissance de 2) pour décoder ~2x maxDim max
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        // 3. Corriger l'orientation d'après les métadonnées EXIF
        val orientation = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        bmp = applyOrientation(bmp, orientation)

        // 4. Mettre à l'échelle pour que le plus grand côté = maxDim
        scaleToMax(bmp, maxDim)
    }.onFailure { Log.w(TAG, "Échec décodage image: ${it.message}") }.getOrNull()

    /** Encode un bitmap en JPEG base64 (sans préfixe data:) pour l'API vision Groq. */
    fun toBase64Jpeg(bitmap: Bitmap, quality: Int = 85): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return src
        }
        return runCatching {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
                .also { if (it != src) src.recycle() }
        }.getOrDefault(src)
    }

    private fun scaleToMax(src: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(src.width, src.height)
        if (largest <= maxDim) return src
        val scale = maxDim.toFloat() / largest
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
            .also { if (it != src) src.recycle() }
    }
}
