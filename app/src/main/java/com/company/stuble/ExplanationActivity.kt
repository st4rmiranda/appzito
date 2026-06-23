package com.company.stuble

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

data class TopicoMapa(
    val titulo: String,
    val itens: List<String>
)

class ExplanationActivity : AppCompatActivity() {

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
                            findViewById<TextView>(R.id.txtExplanationContent).text =
                                textoIA
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

    private fun exibirMapaMental(jsonTexto: String) {
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

            findViewById<TextView>(R.id.txtExplanationContent).visibility = View.GONE

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
}