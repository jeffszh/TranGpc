package cn.jeff.tools.gpc

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.experimental.or
import kotlin.experimental.xor

object GpcConverter {

	@JvmStatic
	fun main(args: Array<String>) {
		println("开始转换……")
		val path = File("TTN2/YOHGAS/GPC/")
		val files = path.listFiles { _, name ->
			name.endsWith(".GPC", ignoreCase = true)
		} ?: throw AssertionError("没有文件？")
		val outputPath = File("png")
		files.forEach { gpcFile ->
			val pngFile = File(
				outputPath, gpcFile.name.replace(
					".GPC", ".png",
					ignoreCase = true
				)
			)
			println("$gpcFile ===> $pngFile")
			convertGpcToPng(gpcFile, pngFile)
		}
		println("转换完成。")
	}

	private fun convertGpcToPng(gpcFile: File, pngFile: File) {
		val data = gpcFile.readBytes()
		decodeGpc(data) { width, height, pixels ->
			val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
			for (y in 0 until height) {
				for (x in 0 until width) {
					val i = x + y * width
					img.raster.setPixel(x, y, pixels[i].asIntArray)
				}
			}
			ImageIO.write(img, "png", pngFile)
		}
	}

	private fun decodeGpc(
		data: ByteArray,
		op: (width: Int, height: Int, pixels: List<Rgb>) -> Unit
	) {
		val source = SeekableByteInputOutputStream(data)
		val scans = source.readWordAt(0x10)
		val paletteOffset = source.readWordAt(0x14)
		val imageOffset = source.readWordAt(0x18)
		source.seek(imageOffset)
		val width = source.readWord()
		val height = source.readWord()
		val bpl = (width + 7) / 8
		val palette = (0 until 16).map { Rgb() }
		palette.forEachIndexed { index, rgb ->
			val palData = source.readWordAt(paletteOffset + index * 2 + 4)
			val (h8bits, l8bits) = HLWord(palData)
			val hByte = HLByte(h8bits)
			val lByte = HLByte(l8bits)
			rgb.g = hByte.l shl 4
			rgb.r = lByte.h shl 4
			rgb.b = lByte.l shl 4
		}

		val bitsBuffer = ByteArray(bpl * 4 * height)
		source.seek(imageOffset + 0x10)
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
					var ch = source.readByte()
					repeat(8) {
						if ((ch and 0x80) != 0) {
							// needs more
							var ch2 = source.readByte()
							repeat(8) {
								if ((ch2 and 0x80) != 0) {
									// load a byte
									decodeBuffer[p++] = source.readByte().toByte()
								} else {
									// a zero byte
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
				val ch = decodeBuffer[0].toInt() and 0xFF
				if (ch != 0) {
					var ch1 = decodeBuffer[1]
					var start = 1
					var p2 = start + ch
					//for (j in ch downTo 1) {
					repeat(ch) {
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
					// var dest = (height - i - 1) * bpl * 4
					// 上面那句是因為原先的程序是要轉換BMP圖片，上下反轉。
					var dest = i * bpl * 4
					for (k in 0 until wpl / 2) {
						val bits = decodeBuffer[src++].toInt() and 0xFF
						if ((bits and 0x80) != 0)
							bitsBuffer[dest] = bitsBuffer[dest] or (0x10 shl j).toByte()
						if ((bits and 0x40) != 0)
							bitsBuffer[dest] = bitsBuffer[dest] or (0x01 shl j).toByte()
						if ((bits and 0x20) != 0)
							bitsBuffer[dest + 1] = bitsBuffer[dest + 1] or (0x10 shl j).toByte()
						if ((bits and 0x10) != 0)
							bitsBuffer[dest + 1] = bitsBuffer[dest + 1] or (0x01 shl j).toByte()
						if ((bits and 0x08) != 0)
							bitsBuffer[dest + 2] = bitsBuffer[dest + 2] or (0x10 shl j).toByte()
						if ((bits and 0x04) != 0)
							bitsBuffer[dest + 2] = bitsBuffer[dest + 2] or (0x01 shl j).toByte()
						if ((bits and 0x02) != 0)
							bitsBuffer[dest + 3] = bitsBuffer[dest + 3] or (0x10 shl j).toByte()
						if ((bits and 0x01) != 0)
							bitsBuffer[dest + 3] = bitsBuffer[dest + 3] or (0x01 shl j).toByte()
						dest += 4
					}
				}
				i += scans
			}
		}

		val pixels = bitsBuffer.flatMap { b ->
			val h = (b.toInt() shr 4) and 0x0F
			val l = b.toInt() and 0x0F
			listOf(h, l)
		}.map { i ->
			palette[i]
		}
		op(width, height, pixels)
	}

	/**
	 * # 可随机访问的字节流
	 */
	private class SeekableByteInputOutputStream(val byteArray: ByteArray) {

		private var pos: Int = 0

		fun seek(offset: Int) {
			pos = offset
		}

		fun readByte(): Int {
			val b = byteArray[pos++]
			return b.toInt() and 0xFF
		}

//		fun readByteAt(offset: Int): Int {
//			seek(offset)
//			return readByte()
//		}

//		fun writeByte(b: Int) {
//			byteArray[pos++] = (b and 0xFF).toByte()
//		}

//		fun writeByteAt(offset: Int, b: Int) {
//			seek(offset)
//			writeByte(b)
//		}

		fun readWord(): Int {
			val l = readByte()
			val h = readByte()
			return HLWord(h, l).value
		}

		fun readWordAt(offset: Int): Int {
			seek(offset)
			return readWord()
		}

	}

	private class HLWord(val h: Int, val l: Int) {
		constructor(int16: Int) : this(
			int16 ushr 8 and 0xFF,
			int16 and 0xFF
		)

		val value get() = h shl 8 or l

		operator fun component1() = h
		operator fun component2() = l
	}

	private class HLByte(val h: Int, val l: Int) {
		constructor(int8: Int) : this(
			(int8 ushr 4) and 0x0F,
			int8 and 0x0F
		)

		operator fun component1() = h
		operator fun component2() = l
	}

}
