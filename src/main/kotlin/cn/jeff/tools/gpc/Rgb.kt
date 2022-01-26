package cn.jeff.tools.gpc

class Rgb(var r: Int, var g: Int, var b: Int) {

	constructor() : this(0, 0, 0)

	val asIntArray get() = arrayOf(r, g, b).toIntArray()

}
