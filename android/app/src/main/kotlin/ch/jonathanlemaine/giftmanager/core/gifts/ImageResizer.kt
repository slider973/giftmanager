package ch.jonathanlemaine.giftmanager.core.gifts

import kotlin.math.max
import kotlin.math.roundToInt

/** Calculs purs du redimensionnement d'une photo avant envoi (iOS : `UIImage.resizedJPEG`). */
object ImageResizer {
    const val MAX_DIMENSION = 1200
    const val JPEG_QUALITY = 80

    data class Size(val width: Int, val height: Int)

    /** Taille finale : le grand côté ramené à [maxDimension] au plus, jamais agrandie. */
    fun targetSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Size {
        val longest = max(width, height)
        if (longest <= maxDimension || longest <= 0) return Size(width, height)
        val scale = maxDimension.toDouble() / longest
        return Size(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
    }

    /**
     * `inSampleSize` de décodage : la plus grande puissance de deux qui garde le grand côté au moins égal à
     * [maxDimension] (le décodeur économise la mémoire, puis l'affinage se fait au redimensionnement exact).
     */
    fun sampleSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }

    /** Rotation puis éventuel miroir horizontal à appliquer pour redresser une image selon son orientation EXIF. */
    data class ExifTransform(val rotationDegrees: Int, val flipHorizontal: Boolean) {
        val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal
    }

    /** Valeurs de `ExifInterface.ORIENTATION_*` (1 à 8) ; toute autre valeur est traitée comme normale. */
    fun exifTransform(orientation: Int): ExifTransform = when (orientation) {
        2 -> ExifTransform(0, true)
        3 -> ExifTransform(180, false)
        4 -> ExifTransform(180, true)
        5 -> ExifTransform(90, true)
        6 -> ExifTransform(90, false)
        7 -> ExifTransform(270, true)
        8 -> ExifTransform(270, false)
        else -> ExifTransform(0, false)
    }
}
