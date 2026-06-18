package com.company.stuble

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

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

        val materiaPesquisada = intent.getStringExtra("MATERIA_PESQUISADA") ?: "Assunto Geral"
        val tipoConteudo = intent.getStringExtra("TIPO_CONTEUDO") ?: "TEXTO"

        findViewById<TextView>(R.id.txtTitleMateria).text = materiaPesquisada.replaceFirstChar { it.uppercase() }

        buscarExploracaoTeoricaIA(materiaPesquisada, tipoConteudo)

        findViewById<MaterialButton>(R.id.btnBackExplanation).setOnClickListener {
            finish()
        }
    }

    private fun buscarExploracaoTeoricaIA(materia: String, tipoConteudo: String) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modeloGemini:generateContent?key=$apiKey"

        val promptText = if (tipoConteudo == "MAPA_MENTAL") {
            """
                Você é o Mentor IA Stuble. Crie um mapa mental estruturado sobre o assunto solicitado pelo estudante: $materia.
                O foco deve ser a revisão rápida e memorização para vestibulares brasileiros (ENEM, FUVEST, UNESP).
                
                Retorne ESTRITAMENTE o código plano de um mapa mental usando a sintaxe 'mermaid' (iniciando obrigatoriamente com a palavra-chave 'mindmap').
                NÃO use blocos de marcação markdown (NÃO coloque aspas triplas ou ```mermaid no início ou no fim). Retorne apenas o texto puro da sintaxe.
                
                Exemplo de formato esperado:
                mindmap
                  root((Assunto Principal))
                    Tema Secundario 1
                      Detalhe Importante
                    Tema Secundario 2
                      Formula Importante
            """.trimIndent()
        } else {
            """
                Você é o Mentor IA Stuble. Explique de forma extremamente didática, focada para vestibulares brasileiros (ENEM, FUVEST, UNESP), o seguinte assunto pedido pelo estudante: $materia.
                
                Siga estas regras de formatação:
                1. Use tópicos claros e diretos.
                2. Destaque fórmulas importantes ou conceitos vitais usando letras maiúsculas ou aspas simples (NÃO use Markdown como asteriscos ou hashtags).
                3. Dê exemplos práticos de como isso costuma cair na prova.
                4. Divida o texto em parágrafos bem espaçados.
            """.trimIndent()
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", promptText)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "text/plain")
            })
        }

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        findViewById<TextView>(R.id.txtExplanationContent).text = "O Mentor IA está estruturando seu conteúdo... Aguarde ;)"

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtExplanationContent).text =
                        "Infelizmente não consegui me conectar ao servidor. Verifique sua conexão de rede."
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val corpo = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(corpo)
                        val textoIA = json.getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0).getString("text")

                        runOnUiThread {
                            if (tipoConteudo == "MAPA_MENTAL") {
                                renderizarMapaMental(textoIA.trim())
                            } else {
                                findViewById<TextView>(R.id.txtExplanationContent).text = textoIA.trim()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EXPLANATION_STUBLE", "Erro no parse: ${e.message}")
                        runOnUiThread {
                            findViewById<TextView>(R.id.txtExplanationContent).text =
                                "Ocorreu um erro ao processar a explicação. Tente pesquisar novamente."
                        }
                    }
                } else {
                    runOnUiThread {
                        findViewById<TextView>(R.id.txtExplanationContent).text =
                            "O Mentor IA está ocupado no momento. Tente novamente em instantes."
                    }
                }
            }
        })
    }

    private fun renderizarMapaMental(codigoMermaid: String) {
        val txtContent = findViewById<TextView>(R.id.txtExplanationContent)
        val webView = findViewById<WebView>(R.id.webViewMapaMental)

        txtContent.visibility = View.GONE
        webView.visibility = View.VISIBLE

        webView.settings.javaScriptEnabled = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        val htmlData = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="[https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js](https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js)"></script>
                <script>
                    mermaid.initialize({
                        startOnLoad: true,
                        theme: 'base',
                        themeVariables: {
                            primaryColor: '#6C63FF',
                            primaryTextColor: '#FFFFFF',
                            lineColor: '#1E1C38',
                            fontSize: '14px',
                            nodeBorder: '#6C63FF'
                        }
                    });
                </script>
                <style>
                    body {
                        background-color: #F7F8FC;
                        margin: 0;
                        padding: 16px;
                        display: flex;
                        justify-content: center;
                    }
                    .mermaid {
                        width: 100%;
                    }
                </style>
            </head>
            <body>
                <div class="mermaid">
                    $codigoMermaid
                </div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
    }
}