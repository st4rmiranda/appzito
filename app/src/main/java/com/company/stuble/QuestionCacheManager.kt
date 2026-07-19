package com.company.stuble

import android.content.Context
import android.util.Log
import com.company.stuble.model.Pergunta
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

object QuestionCacheManager {

    private const val TAG = "QuestionCacheManager"

    private const val PREFS = "question_cache"
    private const val KEY_QUESTIONS = "cached_questions"

    private const val LIMITE_CACHE = 30

    private val gson = Gson()

    fun salvarPergunta(
        context: Context,
        pergunta: Pergunta
    ) {
        val lista = obterPerguntas(context).toMutableList()

        val perguntaJaExiste = lista.any {
            it.pergunta.trim().equals(
                pergunta.pergunta.trim(),
                ignoreCase = true
            )
        }

        if (perguntaJaExiste) {
            Log.d(TAG, "Pergunta repetida não adicionada ao cache.")
            return
        }

        lista.add(pergunta)

        while (lista.size > LIMITE_CACHE) {
            lista.removeAt(0)
        }

        salvarLista(context, lista)
    }

    fun obterProximaPergunta(
        context: Context
    ): Pergunta? {
        val lista = obterPerguntas(context).toMutableList()

        if (lista.isEmpty()) {
            return null
        }

        val pergunta = lista.removeAt(0)

        salvarLista(context, lista)

        return pergunta
    }

    fun obterPerguntas(
        context: Context
    ): List<Pergunta> {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val json = prefs.getString(
            KEY_QUESTIONS,
            null
        ) ?: return emptyList()

        return try {
            val type = object :
                TypeToken<List<Pergunta>>() {}.type

            gson.fromJson<List<Pergunta>>(
                json,
                type
            ) ?: emptyList()

        } catch (erro: JsonSyntaxException) {
            Log.e(
                TAG,
                "Cache antigo ou inválido. O cache será limpo.",
                erro
            )

            limparCache(context)
            emptyList()

        } catch (erro: Exception) {
            Log.e(
                TAG,
                "Erro ao recuperar perguntas do cache.",
                erro
            )

            emptyList()
        }
    }

    fun quantidade(
        context: Context
    ): Int {
        return obterPerguntas(context).size
    }

    fun possuiPerguntas(
        context: Context
    ): Boolean {
        return quantidade(context) > 0
    }

    fun limparCache(
        context: Context
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY_QUESTIONS)
            .apply()
    }

    private fun salvarLista(
        context: Context,
        lista: List<Pergunta>
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_QUESTIONS,
                gson.toJson(lista)
            )
            .apply()
    }
}