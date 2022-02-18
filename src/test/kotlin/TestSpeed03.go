package main

import (
	"sync"
	"time"
)

func main() {
	println("开始")
	channel := make(chan int)
	wg := sync.WaitGroup{}
	wg.Add(2)
	t1 := time.Now().UnixMilli()
	for i := 0; i < 10_000_000; i++ {
		printIt(i)
	}
	t2 := time.Now().UnixMilli()

	go func() {
		defer wg.Done()
		for i := 0; i < 10_000_000; i++ {
			channel <- i
		}
		channel <- -1
	}()
	go func() {
		defer wg.Done()
		for intVal := <-channel; intVal >= 0; intVal = <-channel {
			printIt(intVal)
		}
	}()
	wg.Wait()
	close(channel)
	t3 := time.Now().UnixMilli()

	println("耗时1：%d毫秒；", t2-t1)
	println("耗时2：%d毫秒；", t3-t2)
	println("相差：%d毫秒。", (t3-t2)-(t2-t1))
}

func printIt(intVal int) {
	if intVal%100_000 == 0 {
		println(intVal)
	}
}
