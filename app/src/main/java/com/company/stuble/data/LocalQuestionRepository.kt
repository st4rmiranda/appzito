package com.company.stuble.data

import android.content.Context
import com.company.stuble.model.Pergunta
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class LocalQuestionRepository(private val context: Context) {

    private val gson = Gson()
    private var perguntas: List<Pergunta> = emptyList()

    init {
        carregarPerguntas()
    }

    private fun carregarPerguntas() {
        try {
            val inputStream = context.assets.open("perguntas_locais.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<LocalQuestion>>() {}.type
            val localQuestions: List<LocalQuestion> = gson.fromJson(reader, type)

            perguntas = localQuestions.map { it.toPergunta() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun obterPerguntaAleatoria(area: String? = null): Pergunta? {
        val filtradas = if (area == null) {
            perguntas
        } else {
            perguntas.filter { it.area.contains(area, ignoreCase = true) || area.contains(it.area, ignoreCase = true) }
        }

        if (filtradas.isEmpty() && area != null) {
            return perguntas.randomOrNull()
        }

        return filtradas.randomOrNull()
    }

    private data class LocalQuestion(
        val enunciado: String,
        val alternativas: Map<String, String>,
        val gabarito: String,
        val explicacao: String,
        val materia: String
    ) {
        fun toPergunta(): Pergunta {
            val mapping = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3, "E" to 4)
            val opcoes = listOfNotNull(
                alternativas["A"],
                alternativas["B"],
                alternativas["C"],
                alternativas["D"],
                alternativas["E"]
            )

            return Pergunta(
                pergunta = enunciado,
                opcoes = opcoes,
                correta = mapping[gabarito] ?: 0,
                explicacao = explicacao,
                area = mapearMateriaParaArea(materia)
            )
        }

        private fun mapearMateriaParaArea(materia: String): String {
            return when {
                materia.contains("Matemática", true) -> "Matemática"
                materia.contains("Geografia", true) || 
                materia.contains("História", true) || 
                materia.contains("Filosofia", true) || 
                materia.contains("Sociologia", true) -> "Ciências Humanas"
                materia.contains("Biologia", true) || 
                materia.contains("Física", true) || 
                materia.contains("Química", true) -> "Ciências da Natureza"
                else -> "Linguagens e Códigos"
            }
        }
    }
}
