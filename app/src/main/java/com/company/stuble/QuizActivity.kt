package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
    private var modoExplicacaoAtivo = false

    private var filtroCompetencia: String? = null
    private var ehTreinoLivre = false

    // Configuração de rede unificada
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val modeloGemini = "gemini-2.5-flash"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        // Resgata os filtros enviados pelo SearchFragment
        filtroCompetencia = intent.getStringExtra("COMPETENCIA_FILTRO")
        ehTreinoLivre = intent.getBooleanExtra("EH_TREINO_LIVRE", false)

        buscarPerguntaIA()

        findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
            if (modoExplicacaoAtivo) {
                modoExplicacaoAtivo = false
                proximaQuestaoOuFinalizar()
            } else {
                verificarResposta()
            }
        }

        findViewById<MaterialButton>(R.id.btnVoltar).setOnClickListener {

            val intent = Intent(this, MainActivity::class.java)
            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)
            finish()
        }
    }

    private fun buscarPerguntaIA() {
        runOnUiThread {
            findViewById<MaterialCardView>(R.id.cardExplanation).visibility = View.GONE
            findViewById<TextView>(R.id.txtCount).text = "Questão ${respondidas + 1} de $TOTAL_QUESTOES"

            val progressoPercentual = ((respondidas.toFloat() / TOTAL_QUESTOES) * 100).toInt()
            findViewById<ProgressBar>(R.id.quizProgressBar).progress = progressoPercentual

            findViewById<TextView>(R.id.txtQuestion).text = "Gerando pergunta... Aguarde ;)"
            bloquearBotaoConfirmar(true)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modeloGemini:generateContent?key=$apiKey"

        // DINÂMICA DE FILTRO: Força a IA a gerar sobre o card clicado, ou mantém livre
        val promptFiltro = if (filtroCompetencia != null) {
            "Você DEVE gerar uma pergunta estritamente sobre a competência: $filtroCompetencia."
        } else {
            "As perguntas devem se basear nas seguintes competências alternadas: Linguagens, Códigos e suas Tecnologias, Ciências Humanas e suas Tecnologias, Ciências da Natureza e suas Tecnologias, Matemática e suas Tecnologias."
        }

        val promptText = """
            Gere uma pergunta de vestibular em JSON. $promptFiltro
            Para desenvolver as questões, se baseie nos conteúdos de vestibulares antigos, não só apenas de perguntas de portugues, faça de todas as materias que estão no ENEM, gere perguntas que exercitem tanto o raciocínio lógico quanto a interpretação de texto e as capacidades específicas de cada matéria. As perguntas não devem depender de imagens para serem compreendidas, alterne entre questões consideradas fáceis, médias ou difíceis: 
            {"pergunta":"", "opcoes":["", "", "", ""], "correta":0, "explicacao":""}
            Responda apenas o JSON puro, sem markdown.
        """.trimIndent()

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
                        buscarPerguntaIA()
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
            rb?.isEnabled = true
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
            proximaQuestaoOuFinalizar()
        } else {
            modoExplicacaoAtivo = true

            findViewById<TextView>(R.id.txtExplanation).text = perguntaAtual?.explicacao ?: "Sem explicação disponível."
            findViewById<MaterialCardView>(R.id.cardExplanation).visibility = View.VISIBLE

            for (i in 0 until rg.childCount) {
                rg.getChildAt(i).isEnabled = false
            }

            val btn = findViewById<MaterialButton>(R.id.btnConfirm)
            btn.text = "CONTINUAR"

            findViewById<NestedScrollView>(R.id.quizScrollView).post {
                findViewById<NestedScrollView>(R.id.quizScrollView).fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun proximaQuestaoOuFinalizar() {

        ProgressManager.adicionarQuestaoRespondida(this)

        respondidas++

        val progressoPercentual =
            ((respondidas.toFloat() / TOTAL_QUESTOES) * 100).toInt()

        findViewById<ProgressBar>(R.id.quizProgressBar)
            .progress = progressoPercentual

        if (respondidas >= TOTAL_QUESTOES) {

            Toast.makeText(
                this,
                "Sequência concluída! 🎉",
                Toast.LENGTH_LONG
            ).show()

            finish()

        } else {

            findViewById<MaterialButton>(R.id.btnConfirm)
                .text = "CONFIRMAR RESPOSTA"

            buscarPerguntaIA()
        }
    }

    private fun bloquearBotaoConfirmar(bloquear: Boolean) {
        val btn = findViewById<MaterialButton>(R.id.btnConfirm)
        btn.isEnabled = !bloquear
        btn.text = if (bloquear) "CARREGANDO..." else "CONFIRMAR RESPOSTA"
    }
}