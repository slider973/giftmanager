package ch.jonathanlemaine.giftmanager.core.gifts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prépare une photo choisie avant son envoi (iOS : `UIImage.resizedJPEG`) : décodage allégé (`inSampleSize`),
 * redressement selon l'orientation EXIF, grand côté à 1200 px au plus, JPEG qualité 80. Les calculs sont dans
 * [ImageResizer] ; cette couche ne fait que les appliquer. Hors du fil principal.
 */
class AndroidImageResizer(private val context: Context, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    /** JPEG de [uri], ou `null` si l'image est illisible. */
    suspend fun resizedJpeg(uri: Uri): ByteArray? = withContext(dispatcher) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply { inSampleSize = ImageResizer.sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@withContext null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val exif = ImageResizer.exifTransform(orientation)
        val target = ImageResizer.targetSize(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postScale(target.width.toFloat() / decoded.width, target.height.toFloat() / decoded.height)
            if (exif.rotationDegrees != 0) postRotate(exif.rotationDegrees.toFloat())
            if (exif.flipHorizontal) postScale(-1f, 1f)
        }
        val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, ImageResizer.JPEG_QUALITY, stream)
            if (output !== decoded) decoded.recycle()
            output.recycle()
            stream.toByteArray()
        }
    }
}
