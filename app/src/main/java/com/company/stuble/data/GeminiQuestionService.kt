package com.company.stuble.data

import com.company.stuble.BuildConfig
import com.company.stuble.model.Pergunta
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiQuestionService(
    private val parser: QuestionParser = QuestionParser()
) {

    companion object {
        private const val MODELO = "gemini-2.5-flash"
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .build()

    fun gerarPergunta(
        area: String,
        dificuldade: String
    ): Pergunta {
        val prompt = criarPrompt(area, dificuldade)
        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/" +
                    "v1beta/models/$MODELO:generateContent"
            )
            .header("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .post(
                criarCorpo(prompt)
                    .toRequestBody(JSON_MEDIA_TYPE)
            )
            .build()

        client.newCall(request).execute().use { response ->
            val corpo = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw GeminiHttpException(
                    response.code,
                    mapearErro(response.code)
                )
            }

            return parser.parseApiResponse(corpo, area)
        }
    }

    fun fechar() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    private fun criarPrompt(
        area: String,
        dificuldade: String
    ): String = """
        Gere uma questão curta de vestibular brasileiro.

        Área: $area
        Dificuldade: $dificuldade

        Regras:
        - Enunciado com no máximo 500 caracteres.
        - Exatamente quatro alternativas curtas.
        - Apenas uma alternativa correta.
        - Explicação com no máximo 500 caracteres.
        - Não dependa de imagem.
        - Use conteúdo do Ensino Médio.
        - Não use Markdown.
        - Não escreva nada fora do JSON.
        - O campo "correta" deve ser um número de 0 a 3.

        Retorne:
        {
          "area": "$area",
          "pergunta": "enunciado",
          "opcoes": ["A", "B", "C", "D"],
          "correta": 0,
          "explicacao": "explicação"
        }
    """.trimIndent()

    private fun criarCorpo(prompt: String): String {
        val propriedades = JsonObject().apply {
            add(
                "area",
                JsonObject().apply {
                    addProperty("type", "STRING")
                }
            )
            add(
                "pergunta",
                JsonObject().apply {
                    addProperty("type", "STRING")
                }
            )
            add(
                "opcoes",
                JsonObject().apply {
                    addProperty("type", "ARRAY")
                    addProperty("minItems", 4)
                    addProperty("maxItems", 4)
                    add(
                        "items",
                        JsonObject().apply {
                            addProperty("type", "STRING")
                        }
                    )
                }
            )
            add(
                "correta",
                JsonObject().apply {
                    addProperty("type", "INTEGER")
                    addProperty("minimum", 0)
                    addProperty("maximum", 3)
                }
            )
            add(
                "explicacao",
                JsonObject().apply {
                    addProperty("type", "STRING")
                }
            )
        }

        val schema = JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", propriedades)
            add(
                "required",
                JsonArray().apply {
                    add("area")
                    add("pergunta")
                    add("opcoes")
                    add("correta")
                    add("explicacao")
                }
            )
        }

        return JsonObject().apply {
            add(
                "contents",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            add(
                                "parts",
                                JsonArray().apply {
                                    add(
                                        JsonObject().apply {
                                            addProperty("text", prompt)
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            add(
                "generationConfig",
                JsonObject().apply {
                    addProperty(
                        "responseMimeType",
                        "application/json"
                    )
                    add("responseSchema", schema)
                    addProperty("temperature", 0.2)
                    addProperty("maxOutputTokens", 3000)
                }
            )
        }.toString()
    }

    private fun mapearErro(codigo: Int): String =
        when (codigo) {
            400 -> "A solicitação enviada para a IA é inválida."
            401 -> "A chave da API não foi aceita."
            403 -> "A chave da API não tem permissão."
            404 -> "O modelo configurado não foi encontrado."
            429 -> "O limite da IA foi atingido."
            500 -> "O serviço da IA apresentou erro interno."
            503 -> "O serviço da IA está indisponível."
            else -> "Erro ao gerar questão. Código $codigo."
        }
}

class GeminiHttpException(
    val codigo: Int,
    override val message: String
) : IOException(message)
