package cn.jeff.tools.gpc

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
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
		val scanLines = source.readWordAt(0x10)
		val paletteOffset = source.readWordAt(0x14)
		val imageOffset = source.readWordAt(0x18)
		source.seek(imageOffset)
		val width = source.readWord()
		val height = source.readWord()

		/** 每行的字节数 */
		val bpl = (width + 7) / 8

		/** 调色板 */
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
		val decodeBuffer = SeekableByteInputOutputStream(ByteArray(1024))
		val displayBuffer = SeekableByteInputOutputStream(ByteArray(1024))

		/** 每行的字（16bit）数 */
		val wpl = (bpl * 4 + 1) / 2

		/** 下一行的位置（是奇数，因为每行第一个字节是特殊的，其他才是图像数据。） */
		val nextLinePos = wpl * 2 + 1

		for (scan in 0 until scanLines) {
			// 隔行扫描
			for (lineNo in scan until height step scanLines) {
				// 从源数据解码，解出的数据可能会稍稍超过一行的数据量。
				decodeFromSourceAtLeaseOneLine(source, decodeBuffer, nextLinePos)
				// 第一步的解码对应于最后一步的编码，
				// 可以看出，如果有很多连续或非连续的0，就可以将数据压缩到很小。

				// post decode 1
				xorDecode(decodeBuffer, nextLinePos)
				// post decode 2
				xorToDisplayBuffer(decodeBuffer, displayBuffer, wpl)
				// 这图像格式如此多的异或运算，应该是为了产生更多的0，以便压缩，对卡通图像会相当有效。
				// 第一个异或会让水平方向的相同产生0，第二个异或会让垂直方向的相同产生0。

				// 移走本次处理过的一行的数据，剩余的留到下一行继续处理。
				decodeBuffer.takeOut(nextLinePos)

				// post decode 3
				transposeBits(lineNo, bpl, displayBuffer, bitsBuffer)
				// 最后一步的解码对应于第一步的编码，
				// 按道理说，原始图像因为是卡通，本来就应该有大片的相同，
				// 为何要进行位转置？会增加连续的相同吗？
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
	 * # 解码出超过一行字节数的数据
	 *
	 * 具体过程如下：
	 * * 源数据取出一个字节，8位从高到低，若为0，则填充目标8个字节的0；
	 * * 若不为0，则再从源数据读一个字节。
	 * * 新读的字节再次从高到低8位，若为0，则填充目标一个字节的0；
	 * * 若不为0，则从源数据读一个字节，填入目标中。
	 *
	 * @param source 源缓冲区
	 * @param decodeBuffer 解码目标缓冲区
	 * @param nextLinePos 下一行的位置
	 */
	private fun decodeFromSourceAtLeaseOneLine(
		source: SeekableByteInputOutputStream,
		decodeBuffer: SeekableByteInputOutputStream,
		nextLinePos: Int
	) {
		while (decodeBuffer.pos < nextLinePos) {
			var ch = source.readByte()
			repeat(8) {
				if ((ch and 0x80) != 0) {
					// needs more
					var ch2 = source.readByte()
					repeat(8) {
						if ((ch2 and 0x80) != 0) {
							// load a byte
							decodeBuffer.writeByte(source.readByte())
						} else {
							// a zero byte
							decodeBuffer.writeByte(0)
						}
						ch2 = ch2 shl 1
					}
				} else {
					// 8 bytes of empty
					repeat(8) {
						decodeBuffer.writeByte(0)
					}
				}
				ch = ch shl 1
			}
		}
	}

	/**
	 * # 异或解码
	 *
	 * 算法如下：
	 * 第一个字节为一组数据的长度。
	 * 取出第一组数据的第一个字节，异或到下一组数据的第一个字节，重复直到行末；
	 * 然后回头异或到第一组数据的第二个字节，再异或到下一组数据的第二个字节，直到行末；
	 * 然后回头异或到第一组数据的第三字节……
	 *
	 * @param decodeBuffer 解码缓冲区
	 * @param nextLinePos 下一行的位置
	 */
	private fun xorDecode(decodeBuffer: SeekableByteInputOutputStream, nextLinePos: Int) {
		val distance = decodeBuffer[0].toInt() and 0xFF
		var ch = 0.toByte()
		repeat(distance) { ind ->
			for (p in ind + 1 until nextLinePos step distance) {
				ch = ch xor decodeBuffer[p]
				decodeBuffer[p] = ch
			}
		}
	}

	/**
	 * # 将数据异或到显示缓冲区
	 *
	 * @param decodeBuffer 解码缓冲区
	 * @param wpl 每行的字数
	 */
	private fun xorToDisplayBuffer(
		decodeBuffer: SeekableByteInputOutputStream,
		displayBuffer: SeekableByteInputOutputStream,
		wpl: Int
	) {
		for (j in 0 until wpl * 2) {
			// 注：[decodeBuffer]中第一个字节是特殊的，后续的才是图像数据。
			displayBuffer[j] = displayBuffer[j] xor decodeBuffer[j + 1]
		}
	}

	/**
	 * # 位转置
	 *
	 * 难以用文字说清，看下面示意图：
	 *
	 * ```
	4 planes -> b4b4
	a0 a1 a2 a3 a4 a5 a6 a7
	b0 b1 b2 b3 b4 b5 b6 b7
	c0 c1 c2 c3 c4 c5 c6 c7
	d0 d1 d2 d3 d4 d5 d6 d7
	==>
	a0b0c0d0 a1b1c1d1 ; a2b2c2d2 a3b3c3d3 ; a4b4c4d4 a5b5c5d5 ; a6b6c6d6 a7b7c7d7
	d0c0b0a0 d1c1b1a1 ; 上面那行是錯的，正確的位順序是這行。
	```

	 * @param lineNo 行号
	 * @param bpl 每行的字节数
	 * @param displayBuffer 显示缓冲区
	 * @param bitsBuffer 最终结果的位缓冲区
	 */
	private fun transposeBits(
		lineNo: Int,
		bpl: Int,
		displayBuffer: SeekableByteInputOutputStream,
		bitsBuffer: ByteArray
	) {
		// 先按順序全部讀出來
		displayBuffer.seek(0)
		val originQuadList = List(4) {
			List(bpl) {
				displayBuffer.readByte()
			}
		}
		// 轉置1，將byteIndex作為外層，bitIndex變為內層。
		val quadList = List(bpl) { byteIndex ->
			List(4) { bitIndex ->
				originQuadList[bitIndex][byteIndex]
			}
		}
		// 內層4個字節拆分為4個8bit。
		val bit4x8List = quadList.map { quad ->
			quad.map { byte ->
				(0 until 8).map { shiftCount ->
					(byte shl shiftCount) and 0x80 != 0
				}
			}
		}
		// 轉置2，內層變為8個4bit。
		val bit8x4List = bit4x8List.map { bit4x8 ->
			(0 until 8).map { i ->
				(0 until 4).map { j ->
					bit4x8[j][i]
				}
			}
		}
		// 將最內層的4bit合併為4bit數值。
		val halfByte8List = bit8x4List.map { bit8x4 ->
			bit8x4.map { bits ->
				bits.mapIndexed { index, b ->
					if (b) 0x01 shl index else 0
				}.sum()
			}
		}
		// 將8個4bit數值每兩個合併為高低字節。
		val hl4List = halfByte8List.map { halfByte8 ->
			(0 until 4).map { i ->
				HLByte(halfByte8[i * 2], halfByte8[i * 2 + 1])
			}
		}
		// 平直展開
		val result = hl4List.flatMap { hl4 ->
			hl4.map { hl ->
				hl.value
			}
		}

		val dest = lineNo * bpl * 4
		result.forEachIndexed { index, value ->
			bitsBuffer[dest + index] = value.toByte()
		}

		/*
		displayBuffer.seek(0)
		repeat(4) { j ->
			repeat(bpl) { i ->
				val dest = (lineNo * bpl + i) * 4
				val bits = displayBuffer.readByte()
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
			}
		}
		*/
	}

	/**
	 * # 可随机访问的字节流
	 */
	private class SeekableByteInputOutputStream(val byteArray: ByteArray) {

		/** 流式读写的指针 */
		var pos: Int = 0
			private set

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

		fun writeByte(b: Int) {
			byteArray[pos++] = (b and 0xFF).toByte()
		}

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

		operator fun get(index: Int) =
			byteArray[index]

		operator fun set(index: Int, value: Byte) {
			byteArray[index] = value
		}

		/**
		 * # 取走数据
		 *
		 * 从缓冲区头部，去掉[byteCount]个字节，
		 * 剩余的字节，即从[byteCount] (包括）到[pos] (不包括)的字节，
		 * 向前搬移（搬到最开头）。
		 *
		 * @param byteCount 要取走的字节数。
		 */
		fun takeOut(byteCount: Int) {
			for (i in byteCount until pos) {
				byteArray[i - byteCount] = byteArray[i]
			}
			pos -= byteCount
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

		val value get() = h shl 4 or l

		operator fun component1() = h
		operator fun component2() = l
	}

}
