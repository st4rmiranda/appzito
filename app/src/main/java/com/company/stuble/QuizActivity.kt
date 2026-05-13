package com.company.stuble

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Coloque aqui sua API Key atual.
    // Por segurança, não publique essa chave em projetos reais.
    private val apiKey = "AIzaSyB2jKu3SRs3t__XC311acYuzNCzaSLnCRg"

    // Modelo mais leve para reduzir erro 503 por alta demanda
    private val modeloGemini = "gemini-2.5-flash-lite"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        findViewById<TextView>(R.id.txtQuestion).text = "Gerando pergunta..."

        bloquearBotaoConfirmar(true)

        gerarPerguntaIA()

        findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
            verificarResposta()
        }
    }

    private fun gerarPerguntaIA(tentativa: Int = 1) {
        runOnUiThread {
            findViewById<TextView>(R.id.txtQuestion).text =
                "Gerando pergunta... tentativa $tentativa"

            bloquearBotaoConfirmar(true)
        }

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$modeloGemini:generateContent?key=$apiKey"

        val prompt = """
            Gere UMA pergunta de vestibular em português.

            Responda SOMENTE em JSON válido, sem markdown, sem crases e sem texto extra.

            Use exatamente este formato:
            {
              "pergunta": "texto da pergunta",
              "opcoes": ["alternativa A", "alternativa B", "alternativa C", "alternativa D"],
              "correta": 0,
              "explicacao": "explicação curta da resposta correta"
            }

            Regras obrigatórias:
            - O campo "pergunta" deve ser uma string.
            - O campo "opcoes" deve ter exatamente 4 alternativas.
            - O campo "correta" deve ser um número inteiro de 0 a 3.
            - O campo "explicacao" deve ser uma explicação curta.
            - Não escreva nada antes ou depois do JSON.
            - Não use markdown.
            - Não use crases.
            - Não use json.
        """.trimIndent()

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtQuestion).text =
                        "Erro de rede: ${e.message}"

                    bloquearBotaoConfirmar(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {

                        if ((it.code == 503 || it.code == 429) && tentativa < 3) {
                            runOnUiThread {
                                findViewById<TextView>(R.id.txtQuestion).text =
                                    "Modelo ocupado. Tentando novamente em 2 segundos..."
                            }

                            Handler(Looper.getMainLooper()).postDelayed({
                                gerarPerguntaIA(tentativa + 1)
                            }, 2000)

                            return
                        }

                        runOnUiThread {
                            val mensagem = when (it.code) {
                                400 -> "Erro 400: requisição inválida ou API Key com problema."
                                401 -> "Erro 401: API Key não autorizada."
                                403 -> "Erro 403: sem permissão para usar a Gemini API."
                                404 -> "Erro 404: modelo não encontrado. Verifique o nome do modelo."
                                429 -> "Erro 429: limite de uso atingido. Tente novamente mais tarde."
                                503 -> "Erro 503: modelo ocupado no momento. Tente novamente em alguns segundos."
                                else -> "Erro ${it.code}: não foi possível gerar a pergunta."
                            }

                            findViewById<TextView>(R.id.txtQuestion).text =
                                "$mensagem\n\n$raw"

                            bloquearBotaoConfirmar(false)
                        }

                        return
                    }

                    try {
                        val resposta = JSONObject(raw)

                        val text = resposta
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        exibirNaTela(text)

                    } catch (e: Exception) {
                        runOnUiThread {
                            findViewById<TextView>(R.id.txtQuestion).text =
                                "Erro no formato da resposta: ${e.message}\n\n$raw"

                            bloquearBotaoConfirmar(false)
                        }
                    }
                }
            }
        })
    }

    private fun exibirNaTela(jsonIA: String) {
        try {
            val jsonLimpo = limparJson(jsonIA)

            val pergunta = Gson().fromJson(jsonLimpo, Pergunta::class.java)

            if (pergunta.pergunta.isBlank()) {
                throw Exception("Pergunta vazia")
            }

            if (pergunta.opcoes.size != 4) {
                throw Exception("A IA não retornou exatamente 4 opções")
            }

            if (pergunta.correta !in 0..3) {
                throw Exception("Índice da resposta correta inválido")
            }

            perguntaAtual = pergunta

            runOnUiThread {
                findViewById<TextView>(R.id.txtQuestion).text = pergunta.pergunta

                val rg = findViewById<RadioGroup>(R.id.rgOptions)
                rg.clearCheck()

                for (i in 0 until rg.childCount) {
                    val radio = rg.getChildAt(i) as? RadioButton

                    if (radio != null) {
                        if (i < pergunta.opcoes.size) {
                            radio.text = pergunta.opcoes[i]
                            radio.visibility = View.VISIBLE
                            radio.isEnabled = true
                        } else {
                            radio.text = ""
                            radio.visibility = View.GONE
                            radio.isEnabled = false
                        }
                    }
                }

                bloquearBotaoConfirmar(false)
            }

        } catch (e: Exception) {
            runOnUiThread {
                findViewById<TextView>(R.id.txtQuestion).text =
                    "Erro ao carregar pergunta: ${e.message}\n\nResposta recebida:\n$jsonIA"

                bloquearBotaoConfirmar(false)
            }
        }
    }

    private fun limparJson(texto: String): String {
        var limpo = texto.trim()

        limpo = limpo
            .replace("json", "")
            .replace("JSON", "")
            .replace("", "")
            .trim()

        val inicio = limpo.indexOf("{")
        val fim = limpo.lastIndexOf("}")

        if (inicio == -1 || fim == -1 || fim <= inicio) {
            throw Exception("JSON não encontrado na resposta da IA")
        }

        return limpo.substring(inicio, fim + 1)
    }

    private fun verificarResposta() {
        val pergunta = perguntaAtual

        if (pergunta == null) {
            Toast.makeText(this, "A pergunta ainda não carregou.", Toast.LENGTH_SHORT).show()
            return
        }

        val rg = findViewById<RadioGroup>(R.id.rgOptions)

        if (rg.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Escolha uma alternativa.", Toast.LENGTH_SHORT).show()
            return
        }

        val radioSelecionado = findViewById<RadioButton>(rg.checkedRadioButtonId)
        val selected = rg.indexOfChild(radioSelecionado)

        if (selected < 0 || selected >= pergunta.opcoes.size) {
            Toast.makeText(this, "Alternativa inválida.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selected == pergunta.correta) {
            Toast.makeText(
                this,
                "Acertou! ${pergunta.explicacao}",
                Toast.LENGTH_LONG
            ).show()
        } else {
            val corretaTexto = pergunta.opcoes[pergunta.correta]

            Toast.makeText(
                this,
                "Errou! Resposta correta: $corretaTexto. ${pergunta.explicacao}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun bloquearBotaoConfirmar(bloquear: Boolean) {
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm)

        btnConfirm.isEnabled = !bloquear

        btnConfirm.text = if (bloquear) {
            "CARREGANDO..."
        } else {
            "CONFIRMAR RESPOSTA"
        }
    }
}