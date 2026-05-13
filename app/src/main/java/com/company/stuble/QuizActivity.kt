package com.company.stuble

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Pergunta(
    val pergunta: String,
    val opcoes: List<String>,
    val correta: Int,
    val explicacao: String
)

class QuizActivity : AppCompatActivity() {

    private var perguntaAtual: Pergunta? = null
    private var respondidas = 0
    private val TOTAL_QUESTOES = 20

    // Configuração de rede robusta para evitar quedas
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Sua chave de API
    private val apiKey = "AIzaSyB2jKu3SRs3t__XC311acYuzNCzaSLnCRg"

    // Usando o modelo Flash mais atual para performance máxima
    private val modeloGemini = "gemini-3.1-pro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        // Inicia a primeira pergunta
        buscarPerguntaIA()

        findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
            verificarResposta()
        }
    }

    private fun buscarPerguntaIA() {
        runOnUiThread {
            // Atualiza o contador na tela para o estudante
            findViewById<TextView>(R.id.txtQuestion).text = "Questão ${respondidas + 1} de $TOTAL_QUESTOES\nGerando pergunta..."
            bloquearBotaoConfirmar(true)
        }

        // Endpoint v1beta para suporte aos modelos mais novos
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modeloGemini:generateContent?key=$apiKey"

        val promptText = """
            Gere uma pergunta de vestibular em JSON: 
            {"pergunta":"", "opcoes":["", "", "", ""], "correta":0, "explicacao":""}
            Responda apenas o JSON puro, sem markdown.
        """.trimIndent()

        // Montagem do corpo da requisição com configuração de JSON
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", promptText)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtQuestion).text = "Erro de conexão. Verifique a internet."
                    bloquearBotaoConfirmar(false)
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

                        val inicio = textoIA.indexOf("{")
                        val fim = textoIA.lastIndexOf("}") + 1
                        val p = Gson().fromJson(textoIA.substring(inicio, fim), Pergunta::class.java)

                        perguntaAtual = p
                        runOnUiThread {
                            exibirPerguntaNoLayout(p)
                            bloquearBotaoConfirmar(false)
                        }
                    } catch (e: Exception) {
                        Log.e("QUIZ_STUBLE", "Erro no processamento: ${e.message}")
                        buscarPerguntaIA() // Tenta gerar outra se o JSON vier quebrado
                    }
                } else {
                    runOnUiThread {
                        findViewById<TextView>(R.id.txtQuestion).text = "Erro ${response.code}: Problema na API"
                        bloquearBotaoConfirmar(false)
                    }
                }
            }
        })
    }

    private fun exibirPerguntaNoLayout(p: Pergunta) {
        findViewById<TextView>(R.id.txtQuestion).text = p.pergunta
        val rg = findViewById<RadioGroup>(R.id.rgOptions)
        rg.clearCheck()
        for (i in 0 until rg.childCount) {
            val rb = rg.getChildAt(i) as? RadioButton
            rb?.text = p.opcoes.getOrNull(i) ?: ""
            rb?.visibility = View.VISIBLE
        }
    }

    private fun verificarResposta() {
        val rg = findViewById<RadioGroup>(R.id.rgOptions)
        val selectedId = rg.checkedRadioButtonId

        if (selectedId == -1) {
            Toast.makeText(this, "Selecione uma opção!", Toast.LENGTH_SHORT).show()
            return
        }

        val rbSelecionado = findViewById<RadioButton>(selectedId)
        val indice = rg.indexOfChild(rbSelecionado)

        if (indice == perguntaAtual?.correta) {
            respondidas++
            if (respondidas >= TOTAL_QUESTOES) {
                Toast.makeText(this, "Sequência Concluída! 🎉", Toast.LENGTH_LONG).show()
                finish() // Volta para a tela inicial do seu app
            } else {
                Toast.makeText(this, "Acertou! Carregando próxima...", Toast.LENGTH_SHORT).show()
                buscarPerguntaIA()
            }
        } else {
            Toast.makeText(this, "Errou! ${perguntaAtual?.explicacao}", Toast.LENGTH_LONG).show()
        }
    }

    private fun bloquearBotaoConfirmar(bloquear: Boolean) {
        val btn = findViewById<MaterialButton>(R.id.btnConfirm)
        btn.isEnabled = !bloquear
        btn.text = if (bloquear) "CARREGANDO..." else "CONFIRMAR RESPOSTA"
    }
}