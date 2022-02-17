package main

import (
	. "./misc"
	"fmt"
	"image"
	"image/color"
	"os"
	"strings"
	"time"
)

const filePath = "TTN2/YOHGAS/GPC/"

func main() {
	println("开始转换……")
	t1 := time.Now().UnixMilli()

	for _, gpcFile := range WalkDir(filePath, ".GPC") {
		pngFile := strings.Replace(gpcFile, ".GPC", ".png", 1)
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
			img.Set(x, y, pixels[x+y*width])
		}
	}
	fmt.Printf("width=%d, height=%d\n", width, height)
	//out, _ := os.Create(pngFile)
	//_ = png.Encode(out, img)
}

func decodeGpc(data []byte) (width, height int, pixels []color.RGBA) {
	source := NewSeekableByteInputOutputStream(data)
	//scanLines := source.ReadWordAt(0x10)
	//paletteOffset := source.ReadWordAt(0x14)
	imageOffset := source.ReadWordAt(0x18)
	source.Seek(imageOffset)
	width = source.ReadWord()
	height = source.ReadWord()

	pixels = make([]color.RGBA, width*height)
	return width, height, pixels
}
