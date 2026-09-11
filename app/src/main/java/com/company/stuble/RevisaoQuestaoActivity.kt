package com.company.stuble

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.company.stuble.data.RevisaoErrosManager
import com.company.stuble.model.Pergunta
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson

class RevisaoQuestaoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERGUNTA_JSON = "PERGUNTA_JSON"
    }

    private lateinit var pergunta: Pergunta
    private lateinit var txtEnunciado: TextView
    private lateinit var rgOptions: RadioGroup
    private lateinit var cardExplicacao: MaterialCardView
    private lateinit var txtExplicacao: TextView
    private lateinit var btnConfirmar: MaterialButton

    private var respostaContabilizada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_revisao_questao)

        val json = intent.getStringExtra(EXTRA_PERGUNTA_JSON)
        if (json == null) {
            finish()
            return
        }

        pergunta = Gson().fromJson(json, Pergunta::class.java)

        vincularViews()
        exibirPergunta()

        findViewById<MaterialButton>(R.id.btnVoltarRevisaoQuestao)
            .setOnClickListener { finish() }

        btnConfirmar.setOnClickListener {
            if (respostaContabilizada) {
                finish()
            } else {
                verificarResposta()
            }
        }
    }

    private fun vincularViews() {
        txtEnunciado = findViewById(R.id.txtRevisaoQuestaoEnunciado)
        rgOptions = findViewById(R.id.rgRevisaoOptions)
        cardExplicacao = findViewById(R.id.cardRevisaoExplicacao)
        txtExplicacao = findViewById(R.id.txtRevisaoQuestaoExplicacao)
        btnConfirmar = findViewById(R.id.btnRevisaoConfirmar)
    }

    private fun exibirPergunta() {
        txtEnunciado.text = pergunta.pergunta
        cardExplicacao.isVisible = false

        val opcoes = listOfNotNull(
            findViewById<RadioButton?>(R.id.revisaoOpt1),
            findViewById<RadioButton?>(R.id.revisaoOpt2),
            findViewById<RadioButton?>(R.id.revisaoOpt3),
            findViewById<RadioButton?>(R.id.revisaoOpt4),
            findViewById<RadioButton?>(R.id.revisaoOpt5)
        )

        // Esconde todas as opções primeiro
        opcoes.forEach { it.visibility = View.GONE }

        pergunta.opcoes.forEachIndexed { indice, texto ->
            val radioButton = opcoes.getOrNull(indice) ?: return@forEachIndexed
            radioButton.text = texto
            radioButton.tag = indice
            radioButton.visibility = View.VISIBLE
            radioButton.isEnabled = true
            radioButton.isChecked = false
        }
    }

    private fun verificarResposta() {
        val idSelecionado = rgOptions.checkedRadioButtonId

        if (idSelecionado == -1) {
            Toast.makeText(this, "Selecione uma alternativa.", Toast.LENGTH_SHORT).show()
            return
        }

        val indiceSelecionado = findViewById<RadioButton>(idSelecionado).tag as Int
        val acertou = indiceSelecionado == pergunta.correta

        respostaContabilizada = true

        if (acertou) {
            RevisaoErrosManager.removerErro(this, pergunta)
            Toast.makeText(this, "Resposta correta! ✅", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            for (i in 0 until rgOptions.childCount) {
                rgOptions.getChildAt(i).isEnabled = false
            }
            txtExplicacao.text = pergunta.explicacao
            cardExplicacao.isVisible = true
            btnConfirmar.text = "FECHAR"
        }
    }
}
