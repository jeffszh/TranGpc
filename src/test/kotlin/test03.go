package main

import "../../main/kotlin/cn/jeff/tools/gpc/misc"

func main() {
	data := []byte{
		3, 4, 5, 255, 255, 8,
	}
	println(misc.GetWord(data, 3))
}
