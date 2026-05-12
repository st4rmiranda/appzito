package com.company.stuble

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// O molde da pergunta (fora da classe)
data class Pergunta(
    val pergunta: String,
    val opcoes: List<String>,
    val correta: Int,
    val explicacao: String
)

class QuizActivity : AppCompatActivity() {

    // 1. Variável GLOBAL (para o botão conseguir ler a pergunta depois)
    private var perguntaAtual: Pergunta? = null

    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = "AIzaSyCK6EL-miV2VxPnQo68fxSRL7BWkFAAkVw"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm)
        val rgOptions = findViewById<RadioGroup>(R.id.rgOptions)

        // 2. Lógica do Botão Confirmar
        btnConfirm.setOnClickListener {
            val selectedId = rgOptions.checkedRadioButtonId
            if (selectedId != -1 && perguntaAtual != null) {
                val selectedRB = findViewById<RadioButton>(selectedId)
                val indiceSelecionado = rgOptions.indexOfChild(selectedRB)

                if (indiceSelecionado == perguntaAtual?.correta) {
                    Toast.makeText(this, "Acertou! 🎉", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Errou! ${perguntaAtual?.explicacao}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Selecione uma opção!", Toast.LENGTH_SHORT).show()
            }
        }

        gerarPerguntaIA("História do Brasil")
    }

    private fun gerarPerguntaIA(materia: String) {
        val prompt = "Gere uma pergunta de vestibular sobre $materia em JSON puro."

        // Usando lifecycleScope que é mais seguro que MainScope
        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)
                val texto = response.text
                if (texto != null) {
                    atualizarInterface(texto)
                } else {
                    Toast.makeText(this@QuizActivity, "IA respondeu vazio", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // ISSO VAI MOSTRAR O ERRO REAL NA TELA DO SEU CELULAR
                runOnUiThread {
                    findViewById<TextView>(R.id.txtQuestion).text = "ERRO: ${e.message}"
                    println("DETALHE DO ERRO: ${e.printStackTrace()}")
                }
            }
        }
    }

    private fun atualizarInterface(jsonString: String?) {
        // Limpeza profunda do texto
        val cleanJson = jsonString?.trim()
            ?.removeSurrounding("```json", "```")
            ?.removeSurrounding("```")
            ?.trim()

        try {
            perguntaAtual = Gson().fromJson(cleanJson, Pergunta::class.java)

            runOnUiThread {
                findViewById<TextView>(R.id.txtQuestion).text = perguntaAtual?.pergunta
                val rgOptions = findViewById<RadioGroup>(R.id.rgOptions)

                // Atualiza cada RadioButton
                for (i in 0 until rgOptions.childCount) {
                    (rgOptions.getChildAt(i) as RadioButton).text = perguntaAtual?.opcoes?.get(i)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                // Se cair aqui, a IA mandou um JSON mal formatado
                findViewById<TextView>(R.id.txtQuestion).text = "Erro ao processar pergunta. Tentando novamente..."
                gerarPerguntaIA("História") // Tenta de novo automaticamente
            }
        }
    }
}