package com.company.stuble

import android.os.Bundle
import android.util.Log
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

        // Resgata o termo que o aluno digitou no SearchFragment
        val materiaPesquisada = intent.getStringExtra("MATERIA_PESQUISADA") ?: "Assunto Geral"

        // Atualiza o título na tela capitalizando a primeira letra
        findViewById<TextView>(R.id.txtTitleMateria).text = materiaPesquisada.replaceFirstChar { it.uppercase() }

        // Dispara a chamada para a Inteligência Artificial
        buscarExploracaoTeoricaIA(materiaPesquisada)

        // Configura o botão de fechar a tela
        findViewById<MaterialButton>(R.id.btnBackExplanation).setOnClickListener {
            finish()
        }
    }

    private fun buscarExploracaoTeoricaIA(materia: String) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modeloGemini:generateContent?key=$apiKey"

        // Prompt direcionando a didática da IA para exames brasileiros (ENEM/Vestibulares)
        val promptText = """
            Você é o Mentor IA Stuble. Explique de forma extremamente didática, focada para vestibulares brasileiros (ENEM, FUVEST, UNESP), o seguinte assunto pedido pelo estudante: $materia.
            
            Siga estas regras de formatação:
            1. Use tópicos claros e diretos.
            2. Destaque fórmulas importantes ou conceitos vitais usando letras maiúsculas ou aspas simples (NÃO use Markdown como asteriscos ou hashtags, pois o TextView comum não renderiza nativamente).
            3. Dê exemplos práticos de como isso costuma cair na prova.
            4. Divida o texto em parágrafos bem espaçados.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", promptText)
                ))
            ))
            // Mudamos para texto plano, permitindo que a IA escreva de forma corrida e fluida
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "text/plain")
            })
        }

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

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
                        // Extrai a resposta textual direta fornecida pelo Flash
                        val textoExplicativo = json.getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0).getString("text")

                        runOnUiThread {
                            findViewById<TextView>(R.id.txtExplanationContent).text = textoExplicativo.trim()
                        }
                    } catch (e: Exception) {
                        Log.e("EXPLANATION_STUBLE", "Erro no parse da resposta: ${e.message}")
                        runOnUiThread {
                            findViewById<TextView>(R.id.txtExplanationContent).text =
                                "Ocorreu um erro ao processar a explicação. Tente pesquisar novamente."
                        }
                    }
                } else {
                    runOnUiThread {
                        findViewById<TextView>(R.id.txtExplanationContent).text =
                            "Erro ${response.code}: O Mentor IA está ocupado no momento. Tente novamente em instantes."
                    }
                }
            }
        })
    }
}