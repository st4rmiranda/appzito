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

    // Cliente HTTP atualizado com o interceptador de repetição automática
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor(maxRetries = 3))
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val modeloGemini = "gemini-2.5-flash"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

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
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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

        val promptFiltro = if (filtroCompetencia != null) {
            "Você DEVE gerar uma pergunta estritamente sobre a competência: $filtroCompetencia."
        } else {
            "As perguntas devem se basear nas seguintes competências alternadas: Linguagens, Códigos e suas Tecnologias, Ciências Humanas e suas Tecnologias, Ciências da Natureza e suas Tecnologias, Matemática e suas Tecnologias."
        }

        // PROMPT OTIMIZADO: Remove o viés e força a alternância real de matérias
        val promptText = """
    Você é um gerador especialista em vestibulares brasileiros. 
    ${if (filtroCompetencia != null) "Sua meta absoluta é gerar uma questão sobre: $filtroCompetencia." else "Você DEVE escolher aleatoriamente uma matéria entre as 4 áreas do ENEM: Matemática e suas Tecnologias, Ciências da Natureza (Física, Química, Biologia), Ciências Humanas (História, Geografia, Filosofia/Sociologia) ou Linguagens."}
    
    Regras da estrutura da questão:
    1. Baseie-se no nível e estilo de cobrança de exames reais (ENEM, FUVEST, UNESP).
    2. Crie um enunciado que exercite o raciocínio lógico, a interpretação ou a aplicação de fórmulas específicas da matéria escolhida.
    3. A pergunta não pode depender de imagens ou gráficos para ser respondida.
    4. Alterne o nível de dificuldade (fácil, média ou difícil).
    
    Retorne ESTRITAMENTE o JSON puro no seguinte formato, sem formatação markdown (sem ```json):
    {"pergunta":"[Escreva o enunciado aqui]", "opcoes":["Opção A", "Opção B", "Opção C", "Opção D"], "correta":0, "explicacao":"[Explique o porquê da alternativa correta de forma didática]"}
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

        val progressoPercentual = ((respondidas.toFloat() / TOTAL_QUESTOES) * 100).toInt()
        findViewById<ProgressBar>(R.id.quizProgressBar).progress = progressoPercentual

        if (respondidas >= TOTAL_QUESTOES) {
            Toast.makeText(this, "Sequência concluída! 🎉", Toast.LENGTH_LONG).show()
            finish()
        } else {
            findViewById<MaterialButton>(R.id.btnConfirm).text = "CONFIRMAR RESPOSTA"
            buscarPerguntaIA()
        }
    }

    private fun bloquearBotaoConfirmar(bloquear: Boolean) {
        val btn = findViewById<MaterialButton>(R.id.btnConfirm)
        btn.isEnabled = !bloquear
        btn.text = if (bloquear) "CARREGANDO..." else "CONFIRMAR RESPOSTA"
    }
}