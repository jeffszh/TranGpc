package main

import (
	"./misc"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"os"
	"strings"
	"time"
)

const filePath = "TTN2/YOHGAS/GPC/"

func main() {
	println("开始转换……")
	t1 := time.Now().UnixMilli()

	for _, gpcFile := range misc.WalkDir(filePath, ".GPC") {
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
	out, _ := os.Create(pngFile)
	_ = png.Encode(out, img)
}

func decodeGpc(data []byte) (width, height int, pixels []color.RGBA) {
	width = 0
	height = 0
	pixels = nil
	return width, height, pixels
}
