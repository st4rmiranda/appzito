package com.company.stuble.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuestionParserTest {

    private val parser = QuestionParser()

    // ---- parseQuestionText: formato padrão e tolerância a variações ----

    @Test
    fun `parseQuestionText le um JSON bem formado`() {
        val json = """
            {
              "pergunta": "Qual e a capital do Brasil?",
              "opcoes": ["Rio de Janeiro", "Brasilia", "Sao Paulo", "Salvador"],
              "correta": 1,
              "explicacao": "Brasilia e a capital desde 1960.",
              "area": "Ciências Humanas"
            }
        """.trimIndent()

        val pergunta = parser.parseQuestionText(json, "Linguagens e Códigos")

        assertEquals("Qual e a capital do Brasil?", pergunta.pergunta)
        assertEquals(listOf("Rio de Janeiro", "Brasilia", "Sao Paulo", "Salvador"), pergunta.opcoes)
        assertEquals(1, pergunta.correta)
        assertEquals("Ciências Humanas", pergunta.area)
    }

    @Test
    fun `parseQuestionText remove cercas de markdown`() {
        val json = """
            ```json
            {
              "pergunta": "Pergunta cercada por markdown?",
              "opcoes": ["A", "B", "C", "D"],
              "correta": 0,
              "explicacao": "Explicacao qualquer."
            }
            ```
        """.trimIndent()

        val pergunta = parser.parseQuestionText(json, "Matemática")

        assertEquals("Pergunta cercada por markdown?", pergunta.pergunta)
        assertEquals("Matemática", pergunta.area)
    }

    @Test
    fun `parseQuestionText aceita nomes alternativos de campos`() {
        val json = """
            {
              "questao": "Pergunta com nomes alternativos?",
              "opções": ["A", "B", "C", "D"],
              "resposta": 2,
              "justificativa": "Justificativa qualquer."
            }
        """.trimIndent()

        val pergunta = parser.parseQuestionText(json, "Ciências da Natureza")

        assertEquals("Pergunta com nomes alternativos?", pergunta.pergunta)
        assertEquals("Justificativa qualquer.", pergunta.explicacao)
        assertEquals(2, pergunta.correta)
        // area ausente no JSON: cai para o fallback informado pelo chamador.
        assertEquals("Ciências da Natureza", pergunta.area)
    }

    @Test
    fun `parseQuestionText aceita alternativa correta como letra isolada`() {
        val json = """
            {
              "pergunta": "Pergunta com resposta em letra?",
              "alternativas": ["A", "B", "C", "D"],
              "correta": "C",
              "explicacao": "Explicacao qualquer."
            }
        """.trimIndent()

        val pergunta = parser.parseQuestionText(json, "Matemática")

        assertEquals(2, pergunta.correta)
    }

    @Test
    fun `parseQuestionText aceita alternativa correta em formato por extenso`() {
        val json = """
            {
              "pergunta": "Pergunta com resposta por extenso?",
              "opcoes": ["A", "B", "C", "D"],
              "correta": "Alternativa B",
              "explicacao": "Explicacao qualquer."
            }
        """.trimIndent()

        val pergunta = parser.parseQuestionText(json, "Matemática")

        assertEquals(1, pergunta.correta)
    }

    @Test
    fun `parseQuestionText recupera objeto JSON cercado por texto solto`() {
        val json = """
            Aqui esta a questao que voce pediu:
            {
              "pergunta": "Pergunta com texto ao redor?",
              "opcoes": ["A", "B", "C", "D"],
              "correta": 0,
              "explicacao": "Explicacao qualquer."
            }
            Espero que ajude!
        """.trimIndent()

        val pergunta = parser.parseQuestionText(json, "Matemática")

        assertEquals("Pergunta com texto ao redor?", pergunta.pergunta)
    }

    // ---- parseQuestionText: validação e rejeição ----

    @Test
    fun `parseQuestionText rejeita quantidade errada de alternativas`() {
        val json = """
            {
              "pergunta": "Pergunta com tres alternativas?",
              "opcoes": ["A", "B", "C"],
              "correta": 0,
              "explicacao": "Explicacao qualquer."
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parseQuestionText(json, "Matemática")
        }
    }

    @Test
    fun `parseQuestionText rejeita alternativa em branco`() {
        val json = """
            {
              "pergunta": "Pergunta com alternativa vazia?",
              "opcoes": ["A", "", "C", "D"],
              "correta": 0,
              "explicacao": "Explicacao qualquer."
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parseQuestionText(json, "Matemática")
        }
    }

    @Test
    fun `parseQuestionText rejeita indice de resposta invalido`() {
        val json = """
            {
              "pergunta": "Pergunta com indice invalido?",
              "opcoes": ["A", "B", "C", "D"],
              "correta": "resposta desconhecida",
              "explicacao": "Explicacao qualquer."
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parseQuestionText(json, "Matemática")
        }
    }

    @Test
    fun `parseQuestionText rejeita texto sem nenhum objeto JSON`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseQuestionText("isso nao e json de jeito nenhum", "Matemática")
        }
    }

    // ---- parseApiResponse: envelope da API do Gemini ----

    private fun envelope(texto: String, finishReason: String = "STOP"): String {
        val parte = JsonObject().apply { addProperty("text", texto) }
        val conteudo = JsonObject().apply {
            add("parts", JsonArray().apply { add(parte) })
        }
        val candidato = JsonObject().apply {
            addProperty("finishReason", finishReason)
            add("content", conteudo)
        }
        return JsonObject().apply {
            add("candidates", JsonArray().apply { add(candidato) })
        }.toString()
    }

    @Test
    fun `parseApiResponse le o texto aninhado dentro do envelope da API`() {
        val textoDaPergunta = """
            {
              "pergunta": "Pergunta dentro do envelope?",
              "opcoes": ["A", "B", "C", "D"],
              "correta": 3,
              "explicacao": "Explicacao qualquer.",
              "area": "Matemática"
            }
        """.trimIndent()

        val pergunta = parser.parseApiResponse(envelope(textoDaPergunta), "Linguagens e Códigos")

        assertEquals("Pergunta dentro do envelope?", pergunta.pergunta)
        assertEquals(3, pergunta.correta)
        assertEquals("Matemática", pergunta.area)
    }

    @Test
    fun `parseApiResponse propaga a mensagem de erro da API`() {
        val resposta = JsonObject().apply {
            add("error", JsonObject().apply { addProperty("message", "chave de API invalida") })
        }.toString()

        val erro = assertThrows(IllegalStateException::class.java) {
            parser.parseApiResponse(resposta, "Matemática")
        }

        assertEquals("chave de API invalida", erro.message)
    }

    @Test
    fun `parseApiResponse rejeita resposta sem candidatos`() {
        val resposta = JsonObject().apply { add("candidates", JsonArray()) }.toString()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parseApiResponse(resposta, "Matemática")
        }
    }

    @Test
    fun `parseApiResponse rejeita resposta truncada por limite de tokens`() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseApiResponse(envelope("{}", finishReason = "MAX_TOKENS"), "Matemática")
        }
    }

    @Test
    fun `parseApiResponse rejeita candidato sem texto`() {
        val candidato = JsonObject().apply {
            addProperty("finishReason", "STOP")
            add("content", JsonObject())
        }
        val resposta = JsonObject().apply {
            add("candidates", JsonArray().apply { add(candidato) })
        }.toString()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parseApiResponse(resposta, "Matemática")
        }
    }
}
