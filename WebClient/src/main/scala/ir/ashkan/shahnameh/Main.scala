package ir.ashkan.shahnameh

import ir.ashkan.shahnameh.demo.WebSocketServer
import org.scalajs.dom
import org.scalajs.dom.{WebSocket, document}

object Main {

  def main(args: Array[String]): Unit = {
    document.addEventListener("DOMContentLoaded", { _ =>
      val socket = new WebSocket("ws://127.0.0.1:8080/ws")
      socket.onmessage = { e => println(e.data.toString) }
      socket.onopen = { e =>
        socket.send(WebSocketServer.Message.Ready)
      }
    })
  }
}