package com.m4isper.kmpmlbench.benchmark.data.platform

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

actual fun loadModelBytes(path: String): ByteArray =
    checkNotNull(
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path),
    ) { "Resource not found on classpath: $path" }.use { it.readBytes() }

actual fun loadImageBuffer(path: String): ImageBuffer {
    val bytes = loadModelBytes(path)
    val image = ImageIO.read(ByteArrayInputStream(bytes))
        ?: throw IllegalStateException("Failed to decode image resource: $path")
    val pixels = IntArray(image.width * image.height)
    image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
    return ImageBuffer(image.width, image.height, pixels)
}

actual fun loadImageFile(path: String): ImageBuffer {
    val image = ImageIO.read(File(path))
        ?: throw IllegalStateException("Failed to decode image file: $path")
    val pixels = IntArray(image.width * image.height)
    image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
    return ImageBuffer(image.width, image.height, pixels)
}
