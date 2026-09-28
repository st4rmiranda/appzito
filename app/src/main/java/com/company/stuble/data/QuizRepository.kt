package com.company.stuble.data

import android.content.Context
import android.util.Log
import com.company.stuble.QuestionCacheManager
import com.company.stuble.model.Pergunta
import java.io.IOException

data class QuestionLoadResult(
    val pergunta: Pergunta?,
    val origem: String,
    val mensagemErro: String? = null
)

class QuizRepository(
    private val context: Context,
    private val service: GeminiQuestionService = GeminiQuestionService()
) {
    private val localRepository = LocalQuestionRepository(context)
    private val prefs = context.getSharedPreferences("quiz_rotation_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "QuizRepository"
        private const val PROBABILIDADE_LOCAL = 0.25 
        private const val KEY_INDICE_AREA = "indice_area_persistente"
    }

    private val areas = listOf(
        "Linguagens e Códigos",
        "Ciências Humanas",
        "Ciências da Natureza",
        "Matemática"
    )

    fun obterProximaPergunta(filtroArea: String?, dificuldade: String): QuestionLoadResult {
        val areaDesejada = escolherAreaSemAvançar(filtroArea)

        // Tenta Cache com validação rigorosa
        QuestionCacheManager.obterProximaPergunta(context, areaDesejada)?.let {
            avançarAreaSeNecessario(filtroArea)
            return QuestionLoadResult(pergunta = it, origem = "cache")
        }

        // Tenta Local (Garantindo que nunca repita no mesmo dia)
        if (Math.random() < PROBABILIDADE_LOCAL) {
            obterPerguntaLocalNaoUsada(areaDesejada)?.let {
                avançarAreaSeNecessario(filtroArea)
                return QuestionLoadResult(pergunta = it, origem = "local")
            }
        }

        return gerarComFallback(areaDesejada, dificuldade, filtroArea)
    }

    fun precarregarUmaPergunta(filtroArea: String?, dificuldade: String) {
        if (QuestionCacheManager.quantidade(context, filtroArea) >= 3) return
        val area = escolherAreaSemAvançar(filtroArea)
        try {
            val pergunta = gerarComRetry(area, dificuldade)
            QuestionCacheManager.salvarPergunta(context, pergunta)
        } catch (e: Exception) { Log.w(TAG, "Preload falhou") }
    }

    private fun obterPerguntaLocalNaoUsada(area: String): Pergunta? {
        repeat(5) {
            val p = localRepository.obterPerguntaAleatoria(area)
            if (p != null && !QuestionCacheManager.foiUsadaHoje(context, p)) {
                QuestionCacheManager.marcarComoUsada(context, p)
                return p
            }
        }
        return null
    }

    private fun gerarComFallback(area: String, dificuldade: String, filtro: String?): QuestionLoadResult {
        return try {
            val pergunta = gerarComRetry(area, dificuldade)
            QuestionCacheManager.marcarComoUsada(context, pergunta)
            avançarAreaSeNecessario(filtro)
            QuestionLoadResult(pergunta = pergunta, origem = "gemini")
        } catch (erro: Exception) {
            obterPerguntaLocalNaoUsada(area)?.let {
                avançarAreaSeNecessario(filtro)
                QuestionLoadResult(pergunta = it, origem = "local_fallback")
            } ?: QuestionLoadResult(pergunta = null, origem = "erro", mensagemErro = "Estamos sem conexão. Tente outra matéria!")
        }
    }

    private fun gerarComRetry(area: String, difficulty: String): Pergunta {
        var ultimaEx: Exception? = null
        repeat(2) {
            try {
                val p = service.gerarPergunta(area, difficulty)
                if (!QuestionCacheManager.foiUsadaHoje(context, p)) return p
            } catch (e: Exception) { ultimaEx = e }
        }
        throw ultimaEx ?: IllegalStateException("IA repetindo questões.")
    }

    private fun escolherAreaSemAvançar(filtro: String?): String {
        if (!filtro.isNullOrBlank()) return filtro
        val index = prefs.getInt(KEY_INDICE_AREA, 0)
        return areas[index % areas.size]
    }

    private fun avançarAreaSeNecessario(filtro: String?) {
        if (filtro.isNullOrBlank()) {
            val next = prefs.getInt(KEY_INDICE_AREA, 0) + 1
            prefs.edit().putInt(KEY_INDICE_AREA, next).apply()
        }
    }

    fun fechar() = service.fechar()
}
