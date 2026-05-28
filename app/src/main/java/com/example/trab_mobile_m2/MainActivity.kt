package com.example.trab_mobile_m2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class CardapioItem {
    var id: Int = 0
    var nome: String = ""
    var preco: Double = 0.0
    var imagemLocal: String = ""
}

class MainActivity : AppCompatActivity() {

    private lateinit var containerCardapio: LinearLayout
    private lateinit var dbHelper: CardapioDBHelper
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cardapioUrl = "http://10.0.2.2:8080/cardapio.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        containerCardapio = findViewById(R.id.container_cardapio)
        dbHelper = CardapioDBHelper(this)

        if (isOnline()) {
            carregarDadosOnline()
        } else {
            Toast.makeText(this, "Offline: carregando cache local.", Toast.LENGTH_SHORT).show()
            carregarDadosOffline()
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork

        if (network == null) {
            return false
        }

        val activeNetwork = connectivityManager.getNetworkCapabilities(network)

        if (activeNetwork == null) {
            return false
        }

        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun carregarDadosOnline() {
        Thread {
            try {
                val jsonString = baixarTexto(cardapioUrl)
                val jsonArray = JSONArray(jsonString)
                val itens = ArrayList<CardapioItem>()

                for (i in 0 until jsonArray.length()) {
                    val itemJson = jsonArray.getJSONObject(i)

                    val item = CardapioItem()
                    item.id = itemJson.getInt("id")
                    item.nome = itemJson.getString("nome")
                    item.preco = itemJson.getDouble("preco")

                    val urlImagem = itemJson.getString("url_imagem")
                    item.imagemLocal = baixarESalvarImagem(urlImagem, "prato_" + item.id + ".jpg")

                    itens.add(item)
                }

                salvarCargaLocal(itens)

                mainHandler.post {
                    Toast.makeText(this, "Cardapio atualizado pelo servidor.", Toast.LENGTH_SHORT).show()
                    exibirCardapioNaTela(itens, true)
                }
            } catch (e: Exception) {
                e.printStackTrace()

                mainHandler.post {
                    Toast.makeText(this, "Servidor indisponivel. Usando dados locais.", Toast.LENGTH_LONG).show()
                    carregarDadosOffline()
                }
            }
        }.start()
    }

    private fun baixarTexto(urlString: String): String {
        val url = URL(urlString)
        val conexao = url.openConnection() as HttpURLConnection
        conexao.requestMethod = "GET"
        conexao.connectTimeout = 8000
        conexao.readTimeout = 8000

        try {
            val codigoResposta = conexao.responseCode

            if (codigoResposta < 200 || codigoResposta > 299) {
                throw IllegalStateException("Erro HTTP: " + codigoResposta)
            }

            val inputStream = conexao.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val texto = reader.readText()

            reader.close()
            inputStream.close()

            return texto
        } finally {
            conexao.disconnect()
        }
    }

    private fun baixarESalvarImagem(urlString: String, nomeArquivo: String): String {
        val url = URL(urlString)
        val conexao = url.openConnection() as HttpURLConnection
        conexao.connectTimeout = 8000
        conexao.readTimeout = 8000

        try {
            val codigoResposta = conexao.responseCode

            if (codigoResposta < 200 || codigoResposta > 299) {
                throw IllegalStateException("Erro HTTP: " + codigoResposta)
            }

            val inputStream = BufferedInputStream(conexao.inputStream)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                throw IllegalStateException("Imagem invalida")
            }

            val outputStream = openFileOutput(nomeArquivo, Context.MODE_PRIVATE)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
        } finally {
            conexao.disconnect()
        }

        val arquivo = File(filesDir, nomeArquivo)
        return arquivo.absolutePath
    }

    private fun salvarCargaLocal(itens: ArrayList<CardapioItem>) {
        dbHelper.limparBanco()

        for (i in 0 until itens.size) {
            val item = itens[i]
            dbHelper.salvarPrato(item.id, item.nome, item.preco, item.imagemLocal)
        }
    }

    private fun carregarDadosOffline() {
        val itens = ArrayList<CardapioItem>()
        val db = dbHelper.readableDatabase

        val sql = "SELECT " +
                CardapioDBHelper.COLUMN_ID + ", " +
                CardapioDBHelper.COLUMN_NOME + ", " +
                CardapioDBHelper.COLUMN_PRECO + ", " +
                CardapioDBHelper.COLUMN_IMAGEM_LOCAL +
                " FROM " + CardapioDBHelper.TABLE_NAME +
                " ORDER BY " + CardapioDBHelper.COLUMN_ID

        val cursor = db.rawQuery(sql, null)

        val idCod = cursor.getColumnIndexOrThrow(CardapioDBHelper.COLUMN_ID)
        val nomeCod = cursor.getColumnIndexOrThrow(CardapioDBHelper.COLUMN_NOME)
        val precoCod = cursor.getColumnIndexOrThrow(CardapioDBHelper.COLUMN_PRECO)
        val imagemCod = cursor.getColumnIndexOrThrow(CardapioDBHelper.COLUMN_IMAGEM_LOCAL)

        while (cursor.moveToNext()) {
            val item = CardapioItem()
            item.id = cursor.getInt(idCod)
            item.nome = cursor.getString(nomeCod)
            item.preco = cursor.getDouble(precoCod)
            item.imagemLocal = cursor.getString(imagemCod)

            itens.add(item)
        }

        cursor.close()
        db.close()

        exibirCardapioNaTela(itens, false)
    }

    private fun exibirCardapioNaTela(lista: ArrayList<CardapioItem>, online: Boolean) {
        containerCardapio.removeAllViews()
        containerCardapio.setBackgroundColor(Color.parseColor("#F7F3EE"))
        containerCardapio.setPadding(dp(16), dp(18), dp(16), dp(24))

        adicionarCabecalho(online)

        if (lista.isEmpty()) {
            adicionarMensagemVazia()
            return
        }

        for (i in 0 until lista.size) {
            val item = lista[i]
            val card = criarCardItem(item, online)
            containerCardapio.addView(card)
        }
    }

    private fun adicionarCabecalho(online: Boolean) {
        val titulo = TextView(this)
        titulo.text = "Cardapio do Restaurante"
        titulo.textSize = 26f
        titulo.typeface = Typeface.DEFAULT_BOLD
        titulo.setTextColor(Color.parseColor("#231F20"))
        titulo.gravity = Gravity.CENTER
        containerCardapio.addView(titulo)

        val status = TextView(this)

        if (online) {
            status.text = "Dados recebidos via JSON remoto"
        } else {
            status.text = "Modo offline: precos a consultar"
        }

        status.textSize = 15f
        status.setTextColor(Color.parseColor("#6A5D53"))
        status.gravity = Gravity.CENTER
        status.setPadding(0, dp(4), 0, dp(18))
        containerCardapio.addView(status)
    }

    private fun adicionarMensagemVazia() {
        val mensagem = TextView(this)
        mensagem.text = "Nenhum dado local encontrado. Inicie o servidor e abra o app novamente."
        mensagem.textSize = 17f
        mensagem.setTextColor(Color.parseColor("#5F5148"))
        mensagem.gravity = Gravity.CENTER
        mensagem.setPadding(dp(18), dp(28), dp(18), dp(28))
        containerCardapio.addView(mensagem)
    }

    private fun criarCardItem(item: CardapioItem, online: Boolean): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(14), dp(14), dp(14), dp(16))

        val fundoCard = GradientDrawable()
        fundoCard.setColor(Color.WHITE)
        fundoCard.cornerRadius = dp(10).toFloat()
        fundoCard.setStroke(dp(1), Color.parseColor("#E0D6CC"))
        card.background = fundoCard

        val paramsCard = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        paramsCard.setMargins(0, 0, 0, dp(16))
        card.layoutParams = paramsCard

        val imagem = ImageView(this)
        val paramsImagem = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(210)
        )
        imagem.layoutParams = paramsImagem
        imagem.scaleType = ImageView.ScaleType.CENTER_CROP

        val arquivoFoto = File(item.imagemLocal)

        if (arquivoFoto.exists()) {
            val bitmap = BitmapFactory.decodeFile(arquivoFoto.absolutePath)
            imagem.setImageBitmap(bitmap)
        } else {
            imagem.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        card.addView(imagem)

        val nome = TextView(this)
        nome.text = item.nome
        nome.textSize = 21f
        nome.typeface = Typeface.DEFAULT_BOLD
        nome.setTextColor(Color.parseColor("#231F20"))
        nome.setPadding(0, dp(14), 0, dp(6))
        card.addView(nome)

        val preco = TextView(this)

        if (online) {
            preco.text = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", item.preco)
            preco.setTextColor(Color.parseColor("#1F7A4D"))
        } else {
            preco.text = "a consultar"
            preco.setTextColor(Color.parseColor("#A33A2B"))
        }

        preco.textSize = 18f
        preco.typeface = Typeface.DEFAULT_BOLD
        preco.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        card.addView(preco)

        return card
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        val convertedValue = value * density
        return convertedValue.toInt()
    }
}
