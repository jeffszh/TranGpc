package cn.jeff.tools

import javafx.fxml.FXMLLoader
import javafx.scene.layout.BorderPane
import tornadofx.*

class MainWnd : View("GPC图片转换工具") {

	override val root: BorderPane
	private val j: MainWndJ

	init {
		primaryStage.isResizable = true

		val loader = FXMLLoader()
		root = loader.load(javaClass.getResourceAsStream("/fxml/MainWnd.fxml"))
		j = loader.getController()
		j.k = this
	}

}
