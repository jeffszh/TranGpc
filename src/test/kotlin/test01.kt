import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.experimental.or
import kotlin.experimental.xor

private const val SRC_FILENAME = "TTN2/YOHGAS/GPC/G044.GPC"

fun main() {
	println("开始。")
	val image = BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)
	for (y in 0 until 400) {
		for (x in 0 until 600) {
			image.raster.setPixel(x, y, Rgb(x, y, 0).asIntArray)
		}
	}
	ImageIO.write(image, "png", File("abc.png"))
	println("创建了示例图片。")

	val input = FileInputStream(SRC_FILENAME)
	val buffer = ByteArray(input.available())
	if (input.read(buffer) != buffer.size) {
		throw IOException("搞错了？")
	}
	val scans = buffer.wordAt(0x10)
	val paletteOffset = buffer.wordAt(0x14)
	val imageOffset = buffer.wordAt(0x18)
	val width = buffer.wordAt(imageOffset)
	val height = buffer.wordAt(imageOffset + 2)
	val bpl = (width + 7) / 8
//	println("width=$width, height=$height, bpl=$bpl")
	val colorCount = buffer.wordAt(paletteOffset)
	val transColor = buffer.wordAt(paletteOffset + 2)
	println("colorCount=$colorCount, transColor=$transColor")
	val palette = (0 until 16).map { Rgb() }
	palette.forEachIndexed { index, rgb ->
		val palData = buffer.wordAt(paletteOffset + index * 2 + 4)
		rgb.g = ((palData shr 8) and 0x0F) shl 4
		rgb.r = ((palData shr 4) and 0x0F) shl 4
		rgb.b = (palData and 0x0F) shl 4
	}

	val bitsBuffer = ByteArray(bpl * 4 * height)
	var pos = imageOffset + 0x10
	val decodeBuffer = ByteArray(1024)
	val wpl = (bpl * 4 + 1) / 2
	val nextLinePos = wpl * 2 + 1
	var pTarget = 0
	val disBuf = 0x180
	for (s in 0 until scans) {
		var i = s
		while (i < height) {
			//for (i in s until height step scans)
			var p = pTarget
			while (p < nextLinePos) {
				var ch = buffer[pos++].toInt() and 0xFF
				repeat(8) {
					if ((ch and 0x80) != 0) {
						// needs more
						var ch2 = buffer[pos++].toInt() and 0xFF
						repeat(8) {
							if ((ch2 and 0x80) != 0) {
								// load a byte
								decodeBuffer[p++] = buffer[pos++]
							} else {
								decodeBuffer[p++] = 0
							}
							ch2 = ch2 shl 1
						}
					} else {
						// 8 bytes of empty
						repeat(8) {
							decodeBuffer[p++] = 0
						}
					}
					ch = ch shl 1
				}
			}
			pTarget = p
			// post decode 1
			val ch = decodeBuffer[0].toInt()
			if (ch != 0) {
				var ch1 = decodeBuffer[1]
				var start = 1
				var p2 = start + ch
				for (j in ch downTo 1) {
					while (p2 < nextLinePos) {
						ch1 = ch1 xor decodeBuffer[p2]
						decodeBuffer[p2] = ch1
						p2 += ch
					}
					start++
					p2 = start
				}
			}
			// post decode 2
			for (j in 0 until wpl * 2) {
				decodeBuffer[disBuf + j] = decodeBuffer[disBuf + j] xor decodeBuffer[j + 1]
			}
			// post decode 3
			val size = pTarget - nextLinePos
			for (j in 0 until size) {
				decodeBuffer[j] = decodeBuffer[j + nextLinePos]
			}
			pTarget -= nextLinePos
			var src = disBuf
			// 4 planes -> b4b4
			// a0 a1 a2 a3 a4 a5 a6 a7
			// b0 b1 b2 b3 b4 b5 b6 b7
			// c0 c1 c2 c3 c4 c5 c6 c7
			// d0 d1 d2 d3 d4 d5 d6 d7
			// ==>
			// a0b0c0d0 a1b1c1d1 ; a2b2c2d2 a3b3c3d3 ; a4b4c4d4 a5b5c5d5 ; a6b6c6d6 a7b7c7d7
			for (j in 0 until 4) {
				var dest = (height - i - 1) * bpl * 4
				for (k in 0 until wpl / 2) {
					val bits = decodeBuffer[src++]
					if ((bits.toInt() and 0x80) != 0)
						bitsBuffer[dest] = bitsBuffer[dest] or (0x10 shl j).toByte()
					if ((bits.toInt() and 0x40) != 0)
						bitsBuffer[dest] = bitsBuffer[dest] or (0x01 shl j).toByte()
					if ((bits.toInt() and 0x20) != 0)
						bitsBuffer[dest] = bitsBuffer[dest + 1] or (0x10 shl j).toByte()
					if ((bits.toInt() and 0x10) != 0)
						bitsBuffer[dest] = bitsBuffer[dest + 1] or (0x01 shl j).toByte()
					if ((bits.toInt() and 0x08) != 0)
						bitsBuffer[dest] = bitsBuffer[dest + 2] or (0x10 shl j).toByte()
					if ((bits.toInt() and 0x04) != 0)
						bitsBuffer[dest] = bitsBuffer[dest + 2] or (0x01 shl j).toByte()
					if ((bits.toInt() and 0x02) != 0)
						bitsBuffer[dest] = bitsBuffer[dest + 3] or (0x10 shl j).toByte()
					if ((bits.toInt() and 0x01) != 0)
						bitsBuffer[dest] = bitsBuffer[dest + 3] or (0x01 shl j).toByte()
					dest += 4
				}
			}
			i += scans
		}
	}
	println("行不行呢？")
	FileOutputStream("bits.buf").use {
		it.write(bitsBuffer)
	}
}

private fun ByteArray.wordAt(index: Int): Int {
	val l = get(index)
	val h = get(index + 1)
	return (h.toInt() shl 8) + l
}

private class Rgb(var r: Int, var g: Int, var b: Int) {

	constructor() : this(0, 0, 0)

	val asIntArray get() = arrayOf(r, g, b).toIntArray()

}
