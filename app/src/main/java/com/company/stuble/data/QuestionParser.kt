package com.company.stuble.data

import android.util.Log
import com.company.stuble.model.Pergunta
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

class QuestionParser {

    companion object {
        private const val TAG = "QuestionParser"
    }

    fun parseApiResponse(
        respostaApi: String,
        areaFallback: String
    ): Pergunta {
        val raiz = JsonParser.parseString(respostaApi).asJsonObject

        val erro = raiz.getAsJsonObject("error")
        if (erro != null) {
            throw IllegalStateException(
                erro.get("message")?.asString
                    ?: "A API retornou um erro desconhecido."
            )
        }

        val candidatos = raiz.getAsJsonArray("candidates")
        if (candidatos == null || candidatos.size() == 0) {
            throw IllegalArgumentException(
                "A IA não retornou nenhuma questão."
            )
        }

        val candidato = candidatos[0].asJsonObject
        val finishReason = candidato
            .get("finishReason")
            ?.asString
            .orEmpty()

        if (finishReason == "MAX_TOKENS") {
            throw IllegalArgumentException(
                "A questão ficou longa demais e foi interrompida."
            )
        }

        val parts = candidato
            .getAsJsonObject("content")
            ?.getAsJsonArray("parts")

        if (parts == null || parts.size() == 0) {
            throw IllegalArgumentException(
                "A resposta da IA não contém texto."
            )
        }

        val texto = parts[0]
            .asJsonObject
            .get("text")
            ?.asString
            ?.trim()
            .orEmpty()

        return parseQuestionText(texto, areaFallback)
    }

    fun parseQuestionText(
        textoOriginal: String,
        areaFallback: String
    ): Pergunta {
        val json = extrairObjetoJson(textoOriginal)
        val objeto = JsonParser.parseString(json).asJsonObject

        val perguntaTexto = primeiraString(
            objeto,
            "pergunta",
            "questao",
            "questão",
            "enunciado"
        )

        val explicacao = primeiraString(
            objeto,
            "explicacao",
            "explicação",
            "justificativa"
        )

        val area = primeiraString(
            objeto,
            "area",
            "área",
            "competencia",
            "competência"
        ).ifBlank { areaFallback }

        val arrayOpcoes =
            objeto.getAsJsonArray("opcoes")
                ?: objeto.getAsJsonArray("opções")
                ?: objeto.getAsJsonArray("alternativas")
                ?: throw IllegalArgumentException(
                    "A IA não retornou alternativas."
                )

        if (arrayOpcoes.size() != 4) {
            throw IllegalArgumentException(
                "A IA precisa retornar exatamente quatro alternativas."
            )
        }

        val opcoes = (0 until 4).map { indice ->
            arrayOpcoes[indice].asString.trim()
        }

        val correta = converterIndiceCorreto(
            objeto.get("correta")
                ?: objeto.get("resposta")
                ?: objeto.get("respostaCorreta")
        )

        val pergunta = Pergunta(
            pergunta = perguntaTexto,
            opcoes = opcoes,
            correta = correta,
            explicacao = explicacao,
            area = area
        )

        validar(pergunta)
        return pergunta
    }

    private fun validar(pergunta: Pergunta) {
        require(pergunta.pergunta.isNotBlank()) {
            "Enunciado vazio."
        }
        require(pergunta.opcoes.size == 4) {
            "Quantidade de alternativas inválida."
        }
        require(pergunta.opcoes.none { it.isBlank() }) {
            "Existe alternativa vazia."
        }
        require(pergunta.correta in 0..3) {
            "Índice da resposta correta inválido."
        }
        require(pergunta.explicacao.isNotBlank()) {
            "Explicação vazia."
        }
    }

    private fun primeiraString(
        objeto: JsonObject,
        vararg chaves: String
    ): String {
        for (chave in chaves) {
            val valor = objeto.get(chave) ?: continue
            if (valor.isJsonPrimitive) {
                val texto = valor.asString.trim()
                if (texto.isNotBlank()) return texto
            }
        }
        return ""
    }

    private fun converterIndiceCorreto(
        elemento: JsonElement?
    ): Int {
        if (elemento == null || elemento.isJsonNull) return -1

        if (elemento.isJsonPrimitive) {
            val primitivo = elemento.asJsonPrimitive

            if (primitivo.isNumber) {
                return primitivo.asInt
            }

            val original = primitivo.asString.trim()
            original.toIntOrNull()?.let { return it }

            val valor = original
                .uppercase(Locale.ROOT)
                .replace("OPÇÃO", "ALTERNATIVA")
                .replace("OPCAO", "ALTERNATIVA")

            return when {
                valor == "A" || valor.endsWith(" A") -> 0
                valor == "B" || valor.endsWith(" B") -> 1
                valor == "C" || valor.endsWith(" C") -> 2
                valor == "D" || valor.endsWith(" D") -> 3
                else -> -1
            }
        }

        return -1
    }

    private fun extrairObjetoJson(
        textoOriginal: String
    ): String {
        var texto = textoOriginal
            .trim()
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()

        repeat(2) {
            try {
                val elemento = JsonParser.parseString(texto)
                if (
                    elemento.isJsonPrimitive &&
                    elemento.asJsonPrimitive.isString
                ) {
                    texto = elemento.asString.trim()
                }
            } catch (_: Exception) {
                // Continua com o texto disponível.
            }
        }

        try {
            val elemento = JsonParser.parseString(texto)
            if (elemento.isJsonObject) {
                return elemento.asJsonObject.toString()
            }
        } catch (_: Exception) {
            // Tenta recuperar um trecho.
        }

        val inicio = texto.indexOf('{')
        val fim = texto.lastIndexOf('}')

        if (inicio >= 0 && fim > inicio) {
            val trecho = texto.substring(inicio, fim + 1)
            try {
                val elemento = JsonParser.parseString(trecho)
                if (elemento.isJsonObject) {
                    return elemento.asJsonObject.toString()
                }
            } catch (erro: Exception) {
                Log.w(TAG, "JSON com chaves inválido.", erro)
            }
        }

        val possuiCampos =
            texto.contains("\"pergunta\"", ignoreCase = true) &&
                (
                    texto.contains("\"opcoes\"", ignoreCase = true) ||
                        texto.contains("\"opções\"", ignoreCase = true) ||
                        texto.contains("\"alternativas\"", ignoreCase = true)
                ) &&
                texto.contains("\"correta\"", ignoreCase = true)

        if (possuiCampos) {
            val corrigido = "{${texto.trim().trim(',')}}"
            val elemento = JsonParser.parseString(corrigido)
            if (elemento.isJsonObject) {
                return elemento.asJsonObject.toString()
            }
        }

        throw IllegalArgumentException(
            "Nenhum objeto JSON válido foi encontrado."
        )
    }
}
