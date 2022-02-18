package main

import (
	. "./misc"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const filePath = "TTN2/YOHGAS/GPC/"

func main() {
	println("开始转换……")
	t1 := time.Now().UnixMilli()

	for _, gpcFile := range WalkDir(filePath, ".GPC") {
		//pngFile := strings.Replace(gpcFile, ".GPC", ".png", 1)
		pngFile := "png/" +
			strings.Replace(filepath.Base(gpcFile), ".GPC", ".png", 1)
		convertGpcToPng(gpcFile, pngFile)
	}

	t2 := time.Now().UnixMilli()
	println("转换完成。")
	fmt.Printf("用时：%d毫秒。", t2-t1)
}

func convertGpcToPng(gpcFile string, pngFile string) {
	fmt.Printf("%s ==> %s\n", gpcFile, pngFile)
	data, _ := os.ReadFile(gpcFile)
	width, height, pixels := decodeGpc(data)
	img := image.NewRGBA(image.Rect(0, 0, width, height))
	for y := 0; y < height; y++ {
		for x := 0; x < width; x++ {
			img.Set(x, y, pixels[x][y])
		}
	}
	fmt.Printf("width=%d, height=%d\n", width, height)
	out, _ := os.Create(pngFile)
	_ = png.Encode(out, img)
}

func decodeGpc(data []byte) (width, height int, pixels [][]color.RGBA) {
	source := NewSeekableByteInputOutputStream(data)
	scanLines := source.ReadWordAt(0x10)
	paletteOffset := source.ReadWordAt(0x14)
	imageOffset := source.ReadWordAt(0x18)
	source.Seek(imageOffset)
	width = source.ReadWord()
	height = source.ReadWord()

	// 每行的字节数
	bpl := (width + 7) / 8

	// 调色板
	palette := make([]color.RGBA, 16)
	for i := range palette {
		w16 := source.ReadWordAt(paletteOffset + i*2 + 4)
		palette[i] = color.RGBA{
			R: uint8(w16>>4&0x0F) << 4,
			G: uint8(w16>>8&0x0F) << 4,
			B: uint8(w16&0x0F) << 4,
			A: 255,
		}
	}

	var bitsBufferList [4][]bool
	for i := range bitsBufferList {
		bitsBufferList[i] = make([]bool, bpl*height*8)
	}
	source.Seek(imageOffset + 0x10)

	// 每行的字（16bit）数
	wpl := (bpl*4 + 1) / 2

	channel1 := make(chan byte, 1024)
	channel2 := make(chan byte)
	channel3 := make(chan byte)

	wg := sync.WaitGroup{}
	wg.Add(4)

	// job1
	go decodeFromSource(source, channel1, func() {
		wg.Done()
	})

	// job2
	go func() {
		defer wg.Done()
		for i := 0; i < height; i++ {
			horizontalXorDecode(channel1, channel2, wpl*2)
		}
	}()

	// job3
	go func() {
		defer wg.Done()
		lineBuffer := make([]byte, wpl*2)
		for i := 0; i < height; i++ {
			verticalXorDecode(channel2, channel3, lineBuffer, wpl*2)
		}
	}()

	// job4
	go func() {
		defer wg.Done()
		for scan := 0; scan < scanLines; scan++ {
			// 隔行扫描
			for lineNo := scan; lineNo < height; lineNo += scanLines {
				storeIntoBitsBuffer(lineNo, bpl, channel3, bitsBufferList)
			}
		}
	}()

	// 等待所有job完成
	wg.Wait()
	close(channel1)
	close(channel2)
	close(channel3)

	pixels = make([][]color.RGBA, width)
	for i := range pixels {
		pixels[i] = make([]color.RGBA, height)
	}
	for lineNo := 0; lineNo < height; lineNo++ {
		for colNo := 0; colNo < width; colNo++ {
			sum := 0
			for bitIndex := 0; bitIndex < 4; bitIndex++ {
				if bitsBufferList[bitIndex][lineNo*bpl*8+colNo] {
					sum += 0x01 << bitIndex
				}
			}
			pixels[colNo][lineNo] = palette[sum]
		}
	}
	return width, height, pixels
}

func decodeFromSource(
	source SeekableInputOutputStream,
	outputChannel chan byte,
	done func(),
) {
	defer done()
	for !source.Eof() {
		var ch = source.ReadByte()
		for i := 0; i < 8; i++ {
			if ch&0x80 != 0 {
				if source.Eof() {
					return
				}
				// needs more
				var ch2 = source.ReadByte()
				for i := 0; i < 8; i++ {
					if ch2&0x80 != 0 {
						// load a byte
						outputChannel <- byte(source.ReadByte())
					} else {
						// a zero byte
						outputChannel <- 0
					}
					ch2 <<= 1
				}
			} else {
				// 8 bytes of empty
				for i := 0; i < 8; i++ {
					outputChannel <- 0
				}
			}
			ch <<= 1
		}
	}
}

func horizontalXorDecode(
	inputChannel chan byte, outputChannel chan byte,
	lineLen int,
) {
	distance := int(<-inputChannel)
	buffer := make([]byte, lineLen)
	for i := range buffer {
		buffer[i] = <-inputChannel
	}
	var ch byte = 0
	for ind := 0; ind < distance; ind++ {
		for p := ind; p < lineLen; p += distance {
			ch ^= buffer[p]
			buffer[p] = ch
		}
	}
	for _, b := range buffer {
		outputChannel <- b
	}
}

func verticalXorDecode(
	inputChannel chan byte, outputChannel chan byte,
	lineBuffer []byte,
	lineLen int,
) {
	for ind := 0; ind < lineLen; ind++ {
		ch := <-inputChannel ^ lineBuffer[ind]
		lineBuffer[ind] = ch
		outputChannel <- ch
	}
}

func storeIntoBitsBuffer(
	lineNo int,
	bpl int,
	inputChannel chan byte,
	bitsBufferList [4][]bool,
) {
	dest := lineNo * bpl
	for _, bitArray := range bitsBufferList {
		for byteIndex := 0; byteIndex < bpl; byteIndex++ {
			byteValue := <-inputChannel
			for shiftCount := 0; shiftCount < 8; shiftCount++ {
				bitArray[(dest+byteIndex)*8+shiftCount] =
					0x80>>shiftCount&byteValue != 0
			}
		}
	}
}
