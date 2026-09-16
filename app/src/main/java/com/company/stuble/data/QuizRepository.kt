package com.company.stuble.data

import android.content.Context
import android.util.Log
import com.company.stuble.QuestionCacheManager
import com.company.stuble.model.Pergunta
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

data class QuestionLoadResult(
    val pergunta: Pergunta?,
    val origem: String,
    val mensagemErro: String? = null
)

class QuizRepository(
    private val context: Context,
    private val service: GeminiQuestionService =
        GeminiQuestionService()
) {

    private val localRepository = LocalQuestionRepository(context)

    companion object {
        private const val TAG = "QuizRepository"
        private const val PROBABILIDADE_LOCAL = 0.25 
    }

    private val indiceArea = AtomicInteger(0)

    private val areas = listOf(
        "Linguagens e Códigos",
        "Ciências Humanas",
        "Ciências da Natureza",
        "Matemática"
    )

    fun obterProximaPergunta(
        filtroArea: String?,
        dificuldade: String
    ): QuestionLoadResult {
        
        // 1. Tenta buscar do Cache Primeiro (Evita incrementar indiceArea desnecessariamente)
        QuestionCacheManager
            .obterProximaPergunta(context, filtroArea)
            ?.let {
                return QuestionLoadResult(
                    pergunta = it,
                    origem = "cache"
                )
            }

        // 2. Se não tem cache, define a área e tenta Local ou API
        val area = escolherAreaSemIncrementar(filtroArea)

        // Tenta Local (Garantindo que não seja repetida)
        if (Math.random() < PROBABILIDADE_LOCAL) {
            val local = obterPerguntaLocalNaoUsada(area)
            if (local != null) {
                confirmarConsumoDeArea(filtroArea)
                return QuestionLoadResult(pergunta = local, origem = "local")
            }
        }

        // 3. Tenta gerar via IA
        val resultado = gerarComFallback(area, dificuldade)
        if (resultado.pergunta != null) {
            confirmarConsumoDeArea(filtroArea)
        }
        return resultado
    }

    fun precarregarUmaPergunta(
        filtroArea: String?,
        dificuldade: String
    ) {
        if (QuestionCacheManager.quantidade(context, filtroArea) >= 2) return

        // No preload, usamos a área que seria a "próxima" na sequência
        val area = escolherAreaSemIncrementar(filtroArea)

        try {
            val pergunta = gerarComRetry(area, dificuldade)
            QuestionCacheManager.salvarPergunta(context, pergunta)
        } catch (erro: Exception) {
            Log.w(TAG, "Falha no preload: ${erro.message}")
        }
    }

    private fun obterPerguntaLocalNaoUsada(area: String): Pergunta? {
        // Tenta buscar uma local que não foi usada hoje (máximo 5 tentativas para performance)
        repeat(5) {
            val p = localRepository.obterPerguntaAleatoria(area)
            if (p != null && !QuestionCacheManager.foiUsadaHoje(context, p)) {
                QuestionCacheManager.marcarComoUsada(context, p)
                return p
            }
        }
        return null
    }

    private fun gerarComFallback(
        area: String,
        dificuldade: String
    ): QuestionLoadResult {
        return try {
            val pergunta = gerarComRetry(area, difficulty = dificuldade)
            QuestionCacheManager.marcarComoUsada(context, pergunta)
            QuestionLoadResult(pergunta = pergunta, origem = "gemini")
        } catch (erro: Exception) {
            Log.e(TAG, "Erro Gemini: ${erro.message}. Tentando fallback local.")
            
            val local = obterPerguntaLocalNaoUsada(area)
            if (local != null) {
                QuestionLoadResult(pergunta = local, origem = "local_fallback")
            } else {
                QuestionLoadResult(
                    pergunta = null, 
                    origem = "erro", 
                    mensagemErro = "Ops! Estamos sem conexão e sem questões novas no estoque."
                )
            }
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
        throw ultimaEx ?: IllegalStateException("Erro ao gerar")
    }

    private fun escolherAreaSemIncrementar(filtroArea: String?): String {
        if (!filtroArea.isNullOrBlank()) return filtroArea
        return areas[indiceArea.get() % areas.size]
    }

    private fun confirmarConsumoDeArea(filtroArea: String?) {
        if (filtroArea.isNullOrBlank()) {
            indiceArea.incrementAndGet()
        }
    }

    fun fechar() = service.fechar()
}
