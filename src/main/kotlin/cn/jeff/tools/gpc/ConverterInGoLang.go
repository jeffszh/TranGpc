package main

import (
	"./misc"
	"fmt"
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
}
