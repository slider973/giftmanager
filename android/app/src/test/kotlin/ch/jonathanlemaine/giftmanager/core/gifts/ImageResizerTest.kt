package ch.jonathanlemaine.giftmanager.core.gifts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calculs du redimensionnement des photos (la couche Android se contente de les appliquer). */
class ImageResizerTest {
    @Test fun smallImageIsNeverEnlarged() {
        assertEquals(ImageResizer.Size(800, 600), ImageResizer.targetSize(800, 600))
        assertEquals(ImageResizer.Size(1200, 900), ImageResizer.targetSize(1200, 900))
    }

    @Test fun longestSideIsBroughtDownToTheMaximum() {
        assertEquals(ImageResizer.Size(1200, 900), ImageResizer.targetSize(4000, 3000))
        assertEquals(ImageResizer.Size(900, 1200), ImageResizer.targetSize(3000, 4000))
    }

    @Test fun extremeRatioKeepsAtLeastOnePixel() {
        assertEquals(ImageResizer.Size(1200, 1), ImageResizer.targetSize(100_000, 10))
    }

    @Test fun sampleSizeIsAPowerOfTwoThatKeepsEnoughPixels() {
        assertEquals(1, ImageResizer.sampleSize(1000, 800))
        assertEquals(1, ImageResizer.sampleSize(2399, 100))
        assertEquals(2, ImageResizer.sampleSize(2400, 100))
        assertEquals(2, ImageResizer.sampleSize(4000, 3000))
        assertEquals(8, ImageResizer.sampleSize(9600, 7200))
        listOf(500, 1500, 4032, 12000).forEach { side ->
            val sample = ImageResizer.sampleSize(side, side / 2)
            assertTrue(sample and (sample - 1) == 0)
            assertTrue(side / sample >= minOf(side, ImageResizer.MAX_DIMENSION))
        }
    }

    @Test fun exifOrientationsMapToRotationAndFlip() {
        assertTrue(ImageResizer.exifTransform(1).isIdentity)
        assertTrue(ImageResizer.exifTransform(0).isIdentity)
        assertTrue(ImageResizer.exifTransform(99).isIdentity)
        assertEquals(ImageResizer.ExifTransform(90, false), ImageResizer.exifTransform(6))
        assertEquals(ImageResizer.ExifTransform(270, false), ImageResizer.exifTransform(8))
        assertEquals(ImageResizer.ExifTransform(180, false), ImageResizer.exifTransform(3))
        assertEquals(ImageResizer.ExifTransform(0, true), ImageResizer.exifTransform(2))
        assertEquals(ImageResizer.ExifTransform(180, true), ImageResizer.exifTransform(4))
        assertEquals(ImageResizer.ExifTransform(90, true), ImageResizer.exifTransform(5))
        assertEquals(ImageResizer.ExifTransform(270, true), ImageResizer.exifTransform(7))
    }

    @Test fun jpegSettingsMatchIos() {
        assertEquals(1200, ImageResizer.MAX_DIMENSION)
        assertEquals(80, ImageResizer.JPEG_QUALITY)
    }
}
