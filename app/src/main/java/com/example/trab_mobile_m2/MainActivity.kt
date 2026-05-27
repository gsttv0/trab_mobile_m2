package com.example.trab_mobile_m2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var containerCardapio: LinearLayout
    private lateinit var dbHelper: CardapioDBHelper
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        containerCardapio = findViewById(R.id.container_cardapio)
        dbHelper = CardapioDBHelper(this)

        if (isOnline()) {
            Toast.makeText(this, "Online: Conectando ao servidor local...", Toast.LENGTH_SHORT).show()
            carregarDadosOnline()
        } else {
            Toast.makeText(this, "Offline: Carregando dados do SQLite...", Toast.LENGTH_SHORT).show()
            carregarDadosOffline()
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    // FLUXO ONLINE REAL: Busca do seu servidor local Node.js
    private fun carregarDadosOnline() {
        Thread {
            try {
                // Rota padrão para o emulador Android acessar o localhost do seu PC
                val url = URL("http://10.0.2.2:8080/cardapio.json")
                val conexao = url.openConnection() as HttpURLConnection
                conexao.requestMethod = "GET"
                conexao.connectTimeout = 10000

                val reader = BufferedReader(InputStreamReader(conexao.inputStream))
                val jsonString = reader.use { it.readText() }
                conexao.disconnect()

                val jsonArray = JSONArray(jsonString)
                dbHelper.limparBanco()

                val listaPratos = ArrayList<JSONObject>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val id = item.getInt("id")
                    val nome = item.getString("nome")
                    val preco = item.getDouble("preco")
                    val urlImagem = item.getString("url_imagem")

                    // 1. Salva o texto no SQLite
                    dbHelper.salvarPrato(id, nome, preco)

                    // 2. Faz o download físico da imagem da sua pasta e salva no celular
                    baixarESalvarImagemLocalmente(urlImagem, "prato_$id.jpg")

                    listaPratos.add(item)
                }

                mainHandler.post {
                    exibirCardapioNaTela(listaPratos, online = true)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    Toast.makeText(this, "Servidor desligado. Usando contingência offline.", Toast.LENGTH_LONG).show()
                    carregarDadosOffline()
                }
            }
        }.start()
    }

    private fun carregarDadosOffline() {
        val listaPratos = ArrayList<JSONObject>()
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery("SELECT * FROM ${CardapioDBHelper.TABLE_NAME}", null)

        val idCod = cursor.getColumnIndex(CardapioDBHelper.COLUMN_ID)
        val nomeCod = cursor.getColumnIndex(CardapioDBHelper.COLUMN_NOME)
        val precoCod = cursor.getColumnIndex(CardapioDBHelper.COLUMN_PRECO)

        while (cursor.moveToNext()) {
            val item = JSONObject()
            if (idCod >= 0) item.put("id", cursor.getInt(idCod))
            if (nomeCod >= 0) item.put("nome", cursor.getString(nomeCod))
            if (precoCod >= 0) item.put("preco", cursor.getDouble(precoCod))
            listaPratos.add(item)
        }
        cursor.close()
        db.close()

        exibirCardapioNaTela(listaPratos, online = false)
    }

    private fun baixarESalvarImagemLocalmente(urlString: String, nomeArquivo: String) {
        try {
            val url = URL(urlString)
            val inputStream = url.openStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val outputStream = openFileOutput(nomeArquivo, Context.MODE_PRIVATE)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun exibirCardapioNaTela(lista: List<JSONObject>, online: Boolean) {
        containerCardapio.removeAllViews()

        val txtTitulo = TextView(this).apply {
            text = "Cardápio do Restaurante"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }
        containerCardapio.addView(txtTitulo)

        for (item in lista) {
            val id = item.getInt("id")
            val nome = item.getString("nome")
            val preco = item.optDouble("preco", 0.0)

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 32)
                layoutParams = params
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            }

            val imageView = ImageView(this).apply {
                val imgParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    450
                )
                layoutParams = imgParams
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            // Busca a imagem baixada do armazenamento interno privado do app
            val arquivoFoto = File(filesDir, "prato_$id.jpg")
            if (arquivoFoto.exists()) {
                imageView.setImageBitmap(BitmapFactory.decodeFile(arquivoFoto.absolutePath))
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            itemLayout.addView(imageView)

            val tvNome = TextView(this).apply {
                text = nome
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#222222"))
                setPadding(0, 16, 0, 8)
            }
            itemLayout.addView(tvNome)

            val tvPreco = TextView(this).apply {
                if (online) {
                    text = String.format("R$ %.2f", preco)
                    setTextColor(android.graphics.Color.parseColor("#2E7D32")) // Verde Online
                } else {
                    text = "Preço: A consultar"
                    setTextColor(android.graphics.Color.parseColor("#C62828")) // Vermelho Offline
                }
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            itemLayout.addView(tvPreco)

            containerCardapio.addView(itemLayout)
        }
    }
}