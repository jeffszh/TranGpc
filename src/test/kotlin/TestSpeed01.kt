import java.util.*
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.thread

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

		val thread1 = thread {
			repeat(10_000_000) { i ->
				queue.put(i)
			}
			queue.put(-1)
		}
		val thread2 = thread {
			while (true) {
				val intVal = queue.take()
				if (intVal < 0) break
				printIt(intVal)
			}
		}
		thread1.join()
		thread2.join()
		val t3 = Date().time

		println("耗时1：${t2 - t1}毫秒；")
		println("耗时2：${t3 - t2}毫秒；")
		println("相差：${(t3 - t2) - (t2 - t1)}毫秒。")
	}

	fun printIt(intVal: Int) {
		if (intVal % 100_000 == 0) {
			println(intVal)
		}
	}

}
