package com.m4isper.kmpmlbench.benchmark.domain.processing

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageProcessingTest {
    @Test
    fun generateSyntheticImageHasCorrectSizeAndIsDeterministic() {
        val a = generateSyntheticImage(10, 8, seed = 7)
        val b = generateSyntheticImage(10, 8, seed = 7)
        assertEquals(10, a.width)
        assertEquals(8, a.height)
        assertEquals(80, a.pixels.size)
        assertTrue(a.pixels.contentEquals(b.pixels))
    }

    @Test
    fun differentSeedProducesDifferentImage() {
        val a = generateSyntheticImage(16, 16, seed = 1)
        val b = generateSyntheticImage(16, 16, seed = 2)
        assertTrue(!a.pixels.contentEquals(b.pixels))
    }

    @Test
    fun downsampleShrinksDimensions() {
        val src = generateSyntheticImage(40, 40, seed = 3)
        val ds = downsample(src, 4)
        assertEquals(10, ds.width)
        assertEquals(10, ds.height)
    }

    @Test
    fun downsampleUniformImageKeepsValue() {
        val pixel = (255 shl 24) or (10 shl 16) or (20 shl 8) or 30
        val uniform = ImageBuffer(4, 4, IntArray(16) { pixel })
        val ds = downsample(uniform, 2)
        for (p in ds.pixels) assertEquals(pixel, p)
    }

    @Test
    fun upsampleBilinearGrowsDimensions() {
        val src = generateSyntheticImage(8, 8, seed = 4)
        val up = upsampleBilinear(src, 3)
        assertEquals(24, up.width)
        assertEquals(24, up.height)
        assertTrue(up.pixels.isNotEmpty())
    }

    @Test
    fun psnrIsPerfectForIdenticalBuffers() {
        val img = generateSyntheticImage(12, 12, seed = 5)
        assertEquals(100.0, computePsnr(img, img), 0.0)
    }

    @Test
    fun psnrIsFiniteAndLowerForDifferentBuffers() {
        val ref = generateSyntheticImage(16, 16, seed = 5)
        val other = generateSyntheticImage(16, 16, seed = 9)
        val psnr = computePsnr(ref, other)
        assertTrue(psnr.isFinite())
        assertTrue(psnr < 100.0)
        assertTrue(psnr > 0.0)
    }

    @Test
    fun ssimIsOneForIdenticalBuffers() {
        val img = generateSyntheticImage(12, 12, seed = 6)
        assertEquals(1.0, computeSsim(img, img), 0.0001)
    }

    @Test
    fun ssimIsBelowOneForDifferentBuffers() {
        val ref = generateSyntheticImage(16, 16, seed = 6)
        val other = generateSyntheticImage(16, 16, seed = 11)
        val ssim = computeSsim(ref, other)
        assertTrue(ssim <= 1.0)
        assertTrue(ssim > 0.0)
    }
}
