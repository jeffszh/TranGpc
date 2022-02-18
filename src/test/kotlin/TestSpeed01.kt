import java.util.*
import java.util.concurrent.SynchronousQueue

object TestSpeed01 {

	@JvmStatic
	fun main(args: Array<String>) {
		println("开始")
		val queue = SynchronousQueue<Int>()
		val t1 = Date().time
		repeat(10_000_000) { i ->
			printIt(i)
		}
		val t2 = Date().time

		println("耗时：${t2 - t1}毫秒。")
	}

	fun printIt(intVal: Int) {
		if (intVal % 100_000 == 0) {
			println(intVal)
		}
	}

}
