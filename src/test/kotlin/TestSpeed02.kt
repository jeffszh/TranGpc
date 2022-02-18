import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

object TestSpeed02 {

	@JvmStatic
	fun main(args: Array<String>) {
		println("开始")
		val channel = Channel<Int>()
		val t1 = Date().time
		repeat(10_000_000) { i ->
			TestSpeed01.printIt(i)
		}
		val t2 = Date().time

		val job1 = GlobalScope.launch {
			repeat(10_000_000) { i ->
				channel.send(i)
			}
			channel.send(-1)
		}
		val job2 = GlobalScope.launch {
			while (true) {
				val intVal = channel.receive()
				if (intVal < 0) break
				TestSpeed01.printIt(intVal)
			}
		}
		runBlocking {
			job1.join()
			job2.join()
			channel.close()
		}
		val t3 = Date().time

		println("耗时1：${t2 - t1}毫秒；")
		println("耗时2：${t3 - t2}毫秒；")
		println("相差：${(t3 - t2) - (t2 - t1)}毫秒。")
	}

}
