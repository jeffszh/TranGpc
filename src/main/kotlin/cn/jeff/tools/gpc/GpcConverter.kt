package cn.jeff.tools.gpc

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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
			val pngFile = File(outputPath, gpcFile.name + ".png")
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
		// TODO
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

		fun readByteAt(offset: Int): Int {
			seek(offset)
			return readByte()
		}

		fun writeByte(b: Int) {
			byteArray[pos++] = (b and 0xFF).toByte()
		}

		fun writeByteAt(offset: Int, b: Int) {
			seek(offset)
			writeByte(b)
		}

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
