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

    companion object {
        private const val TAG = "QuizRepository"
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
        val area = escolherArea(filtroArea)

        QuestionCacheManager
            .obterProximaPergunta(context, filtroArea)
            ?.let {
                return QuestionLoadResult(
                    pergunta = it,
                    origem = "cache"
                )
            }

        return gerarComFallback(area, dificuldade)
    }

    fun precarregarUmaPergunta(
        filtroArea: String?,
        dificuldade: String
    ) {
        if (
            QuestionCacheManager.quantidade(
                context,
                filtroArea
            ) >= 2
        ) {
            return
        }

        val area = escolherArea(filtroArea)

        try {
            val pergunta = gerarComRetry(area, dificuldade)
            QuestionCacheManager.salvarPergunta(context, pergunta)
        } catch (erro: Exception) {
            Log.w(
                TAG,
                "Não foi possível pré-carregar pergunta.",
                erro
            )
        }
    }

    fun fechar() {
        service.fechar()
    }

    private fun gerarComFallback(
        area: String,
        dificuldade: String
    ): QuestionLoadResult {
        return try {
            val pergunta = gerarComRetry(area, dificuldade)
            QuestionCacheManager.marcarComoUsada(
                context,
                pergunta
            )

            QuestionLoadResult(
                pergunta = pergunta,
                origem = "gemini"
            )
        } catch (erro: Exception) {
            val cache = QuestionCacheManager
                .obterProximaPergunta(context, null)

            if (cache != null) {
                QuestionLoadResult(
                    pergunta = cache,
                    origem = "cache_fallback",
                    mensagemErro = erro.message
                )
            } else {
                QuestionLoadResult(
                    pergunta = null,
                    origem = "erro",
                    mensagemErro = erro.message
                        ?: "Não foi possível carregar a questão."
                )
            }
        }
    }

    private fun gerarComRetry(
        area: String,
        dificuldade: String
    ): Pergunta {
        var ultimoErro: Exception? = null

        repeat(2) { tentativa ->
            try {
                val pergunta = service.gerarPergunta(
                    area,
                    dificuldade
                )

                if (
                    QuestionCacheManager.foiUsadaHoje(
                        context,
                        pergunta
                    )
                ) {
                    throw IllegalStateException(
                        "A IA repetiu uma questão usada hoje."
                    )
                }

                return pergunta
            } catch (erro: Exception) {
                ultimoErro = erro

                val podeTentarNovamente =
                    erro is IOException &&
                        erro !is GeminiHttpException ||
                        (
                            erro is GeminiHttpException &&
                                erro.codigo in listOf(500, 503)
                        )

                if (!podeTentarNovamente || tentativa == 1) {
                    throw erro
                }

                Thread.sleep(1200L)
            }
        }

        throw ultimoErro
            ?: IllegalStateException("Erro desconhecido.")
    }

    private fun escolherArea(filtroArea: String?): String {
        if (!filtroArea.isNullOrBlank()) {
            return filtroArea
        }

        val indice = indiceArea.getAndIncrement()
        return areas[indice % areas.size]
    }
}
