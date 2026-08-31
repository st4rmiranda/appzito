package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

data class TopicoMapa(
    val titulo: String,
    val itens: List<String>
)

class ExplanationActivity : AppCompatActivity() {
    private var topicosAtuais =
        emptyList<TopicoMapa>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val modeloGemini = "gemini-2.5-flash"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explanation)

        val materiaPesquisada =
            intent.getStringExtra("MATERIA_PESQUISADA") ?: "Assunto Geral"

        val tipoConteudo =
            intent.getStringExtra("TIPO_CONTEUDO") ?: "TEXTO"

        findViewById<TextView>(R.id.txtTitleMateria).text =
            materiaPesquisada.replaceFirstChar { it.uppercase() }

        findViewById<MaterialButton>(R.id.btnBackExplanation).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btnGerarPdfMapa).setOnClickListener {
            if (topicosAtuais.isNotEmpty()) {
                gerarPdfCompleto(materiaPesquisada, topicosAtuais)
            }
        }

        buscarConteudoIA(materiaPesquisada, tipoConteudo)
    }

    private fun buscarConteudoIA(materia: String, tipoConteudo: String) {
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$modeloGemini:generateContent?key=$apiKey"

        val promptText = if (tipoConteudo == "MAPA_MENTAL") {
            """
            Crie um mapa mental educacional sobre: $materia

            Retorne APENAS JSON válido, sem markdown e sem texto adicional.

            Use exatamente este formato:

            {
              "titulo": "$materia",
              "topicos": [
                {
                  "titulo": "Conceito",
                  "itens": ["item 1", "item 2"]
                },
                {
                  "titulo": "Formula",
                  "itens": ["item 1", "item 2"]
                }
              ]
            }

            Regras:
            - Use linguagem simples para estudantes de ensino médio.
            - Foque em ENEM, FUVEST e UNESP.
            - Crie entre 4 e 6 tópicos.
            - Cada tópico deve ter entre 2 e 4 itens.
            - Não use markdown.
            - Não use ```json.
            - Não escreva nada fora do JSON.
            """.trimIndent()
        } else {
            """
            Você é o Mentor IA Stuble. Explique de forma didática, focada em vestibulares brasileiros
            como ENEM, FUVEST e UNESP, o seguinte assunto: $materia.

            Regras:
            1. Use tópicos claros.
            2. Explique os conceitos principais.
            3. Dê exemplos de aplicação.
            4. Mostre como o tema costuma cair em provas.
            5. Não use markdown com asteriscos ou hashtags.
            """.trimIndent()
        }

        val jsonBody = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", promptText)
                        )
                    )
                )
            )
        }

        val body = jsonBody.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        findViewById<TextView>(R.id.txtExplanationContent).visibility = View.VISIBLE
        findViewById<RecyclerView>(R.id.recyclerMapaMental).visibility = View.GONE
        findViewById<MaterialButton>(R.id.btnGerarPdfMapa).visibility = View.GONE

        findViewById<TextView>(R.id.txtExplanationContent).text =
            "O Mentor IA está estruturando seu conteúdo... Aguarde ;)"

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtExplanationContent).text =
                        "Não foi possível conectar ao servidor. Verifique sua internet."
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val corpo = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e("GEMINI_ERROR", "Código: ${response.code}\nResposta: $corpo")

                    runOnUiThread {
                        findViewById<TextView>(R.id.txtExplanationContent).text =
                            "Erro ${response.code}. Tente novamente em instantes."
                    }
                    return
                }

                try {
                    val json = JSONObject(corpo)

                    val textoIA = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    Log.d("MAPA_JSON_DEBUG", textoIA)

                    runOnUiThread {
                        if (tipoConteudo == "MAPA_MENTAL") {
                            exibirMapaMental(textoIA)
                        } else {
                            findViewById<MaterialButton>(R.id.btnGerarPdfMapa).visibility = View.GONE
                            val textoFormatado = formatarTextoIA(textoIA)
                            findViewById<TextView>(R.id.txtExplanationContent).text =
                                textoFormatado
                        }
                    }

                } catch (e: Exception) {
                    Log.e("EXPLANATION_ERROR", e.message ?: "Erro desconhecido")

                    runOnUiThread {
                        findViewById<TextView>(R.id.txtExplanationContent).text =
                            "Ocorreu um erro ao processar a resposta da IA."
                    }
                }
            }
        })
    }

    private fun formatarTextoIA(texto: String): String {
        return texto
            .replace(Regex("\\*\\*"), "") // Remove bold markdown
            .replace(Regex("###"), "")    // Remove headers
            .replace(Regex("##"), "")
            .replace(Regex("#"), "")
            .replace(Regex("^\\*\\s+", RegexOption.MULTILINE), "• ") // Marcadores
            .replace(Regex("(?m)^-\\s+"), "• ") // Outro tipo de marcador
            .replace(Regex("\\n{3,}"), "\n\n") // Evita muitos espaços vazios
            .trim()
    }

    private fun exibirMapaMental(jsonTexto: String) {

        val listaTopicos = mutableListOf<TopicoMapa>()

        try {
            val jsonLimpo = jsonTexto
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val objeto = JSONObject(jsonLimpo)
            val topicosArray = objeto.getJSONArray("topicos")

            val listaTopicos = mutableListOf<TopicoMapa>()

            for (i in 0 until topicosArray.length()) {
                val topicoJson = topicosArray.getJSONObject(i)

                val titulo = topicoJson.getString("titulo")
                val itensArray = topicoJson.getJSONArray("itens")

                val itens = mutableListOf<String>()

                for (j in 0 until itensArray.length()) {
                    itens.add(itensArray.getString(j))
                }

                listaTopicos.add(
                    TopicoMapa(
                        titulo = titulo,
                        itens = itens
                    )
                )
            }

            this.topicosAtuais = listaTopicos

            findViewById<TextView>(R.id.txtExplanationContent).visibility = View.GONE

            findViewById<MaterialButton>(R.id.btnGerarPdfMapa).visibility = View.VISIBLE

            val recycler = findViewById<RecyclerView>(R.id.recyclerMapaMental)
            recycler.visibility = View.VISIBLE
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = MapaMentalAdapter(listaTopicos)

        } catch (e: Exception) {
            Log.e("MAPA_MENTAL_ERROR", e.message ?: "Erro ao montar mapa mental")

            findViewById<TextView>(R.id.txtExplanationContent).visibility = View.VISIBLE
            findViewById<RecyclerView>(R.id.recyclerMapaMental).visibility = View.GONE

            findViewById<TextView>(R.id.txtExplanationContent).text =
                "A IA não conseguiu gerar um mapa mental válido. Tente pesquisar novamente."
        }
    }
    private fun gerarPdfCompleto(
        tituloMapa: String,
        topicos: List<TopicoMapa>
    ) {

        val document = PdfDocument()

        var numeroPagina = 1

        var pageInfo = PdfDocument.PageInfo.Builder(
            595,
            842,
            numeroPagina
        ).create()

        var page = document.startPage(pageInfo)

        var canvas = page.canvas

        val tituloPaint = Paint().apply {
            color = android.graphics.Color.rgb(
                79,
                70,
                229
            )
            textSize = 26f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtituloPaint = Paint().apply {
            color = android.graphics.Color.rgb(
                100,
                116,
                139
            )
            textSize = 13f
            isAntiAlias = true
        }

        val topicoPaint = Paint().apply {
            color = android.graphics.Color.rgb(
                79,
                70,
                229
            )
            textSize = 19f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textoPaint = Paint().apply {
            color = android.graphics.Color.rgb(
                45,
                50,
                67
            )
            textSize = 14f
            isAntiAlias = true
        }

        var y = 55f

        canvas.drawText(
            tituloMapa,
            40f,
            y,
            tituloPaint
        )

        y += 28f

        canvas.drawText(
            "Mapa mental gerado pelo Mentor IA do Stuble",
            40f,
            y,
            subtituloPaint
        )

        y += 45f

        for (topico in topicos) {

            // Verifica se ainda existe espaço suficiente
            // para o título e pelo menos um item.
            if (y > 770f) {

                document.finishPage(page)

                numeroPagina++

                pageInfo = PdfDocument.PageInfo.Builder(
                    595,
                    842,
                    numeroPagina
                ).create()

                page = document.startPage(pageInfo)

                canvas = page.canvas

                y = 55f
            }

            canvas.drawText(
                topico.titulo,
                40f,
                y,
                topicoPaint
            )

            y += 28f

            for (item in topico.itens) {

                if (y > 800f) {

                    document.finishPage(page)

                    numeroPagina++

                    pageInfo =
                        PdfDocument.PageInfo.Builder(
                            595,
                            842,
                            numeroPagina
                        ).create()

                    page = document.startPage(pageInfo)

                    canvas = page.canvas

                    y = 55f
                }

                canvas.drawText(
                    "• $item",
                    50f,
                    y,
                    textoPaint
                )

                y += 25f
            }

            y += 18f
        }

        document.finishPage(page)

        try {

            val pasta = File(
                cacheDir,
                "mapas_mentais"
            )

            if (!pasta.exists()) {
                pasta.mkdirs()
            }

            val arquivo = File(
                pasta,
                "mapa_mental_${System.currentTimeMillis()}.pdf"
            )

            document.writeTo(
                arquivo.outputStream()
            )

            document.close()

            compartilharPdf(arquivo)

        } catch (e: Exception) {

            document.close()

            Toast.makeText(
                this,
                "Não foi possível gerar o PDF.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun compartilharPdf(
        arquivo: File
    ) {

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            arquivo
        )

        val intent = Intent(
            Intent.ACTION_SEND
        ).apply {

            type = "application/pdf"

            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        startActivity(
            Intent.createChooser(
                intent,
                "Compartilhar mapa mental"
            )
        )
    }

}