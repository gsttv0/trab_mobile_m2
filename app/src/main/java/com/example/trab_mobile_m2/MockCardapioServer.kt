package com.example.trab_mobile_m2

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class MockCardapioServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var running = false
    private var port: Int = 0

    fun start() {
        if (running) {
            return
        }

        val localAddress = InetAddress.getByName("127.0.0.1")
        serverSocket = ServerSocket(0, 50, localAddress)
        port = serverSocket!!.localPort
        running = true

        Thread {
            while (running) {
                try {
                    val client = serverSocket!!.accept()
                    atenderCliente(client)
                } catch (e: Exception) {
                    if (running) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    fun stop() {
        running = false
        serverSocket?.close()
    }

    fun getCardapioUrl(): String {
        return "http://127.0.0.1:" + port + "/cardapio.json"
    }

    private fun atenderCliente(client: Socket) {
        Thread {
            try {
                val input = client.getInputStream()
                val output = client.getOutputStream()
                val request = ByteArray(2048)
                val bytesRead = input.read(request)

                if (bytesRead <= 0) {
                    client.close()
                    return@Thread
                }

                val requestText = String(request, 0, bytesRead)
                val firstLine = requestText.lines()[0]
                val parts = firstLine.split(" ")

                if (parts.size < 2) {
                    enviarResposta(output, 400, "text/plain", "Bad request".toByteArray())
                    client.close()
                    return@Thread
                }

                val path = parts[1]

                if (path == "/" || path == "/cardapio.json") {
                    val json = montarJsonCardapio()
                    enviarResposta(output, 200, "application/json", json.toByteArray())
                } else if (path == "/foto1.jpg") {
                    enviarAsset(output, "foto1.jpg")
                } else if (path == "/foto2.jpg") {
                    enviarAsset(output, "foto2.jpg")
                } else if (path == "/foto3.jpg") {
                    enviarAsset(output, "foto3.jpg")
                } else if (path == "/foto4.jpg") {
                    enviarAsset(output, "foto4.jpg")
                } else {
                    enviarResposta(output, 404, "text/plain", "Not found".toByteArray())
                }

                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
                client.close()
            }
        }.start()
    }

    private fun montarJsonCardapio(): String {
        val baseUrl = "http://127.0.0.1:" + port

        return "[" +
                "{\"id\":1,\"nome\":\"Salada Proteica\",\"preco\":34.90,\"url_imagem\":\"" + baseUrl + "/foto1.jpg\"}," +
                "{\"id\":2,\"nome\":\"Pizza\",\"preco\":49.90,\"url_imagem\":\"" + baseUrl + "/foto2.jpg\"}," +
                "{\"id\":3,\"nome\":\"Fatia de Bolo\",\"preco\":24.90,\"url_imagem\":\"" + baseUrl + "/foto3.jpg\"}," +
                "{\"id\":4,\"nome\":\"Macarrao\",\"preco\":37.90,\"url_imagem\":\"" + baseUrl + "/foto4.jpg\"}" +
                "]"
    }

    private fun enviarAsset(output: java.io.OutputStream, assetName: String) {
        val input = context.assets.open(assetName)
        val buffer = ByteArray(4096)
        val byteArrayOutput = ByteArrayOutputStream()

        var read = input.read(buffer)
        while (read != -1) {
            byteArrayOutput.write(buffer, 0, read)
            read = input.read(buffer)
        }

        input.close()
        enviarResposta(output, 200, "image/jpeg", byteArrayOutput.toByteArray())
    }

    private fun enviarResposta(
        output: java.io.OutputStream,
        statusCode: Int,
        contentType: String,
        body: ByteArray
    ) {
        val statusText = if (statusCode == 200) "OK" else "ERROR"
        val header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + body.size + "\r\n" +
                "Connection: close\r\n" +
                "\r\n"

        output.write(header.toByteArray())
        output.write(body)
        output.flush()
    }
}
