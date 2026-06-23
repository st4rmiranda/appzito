package com.company.stuble

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object QuestionCacheManager {

    private const val PREFS = "question_cache"
    private const val KEY_QUESTIONS = "cached_questions"

    fun salvarPergunta(context: Context, pergunta: Pergunta) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lista = obterPerguntas(context).toMutableList()

        lista.add(pergunta)

        prefs.edit()
            .putString(KEY_QUESTIONS, Gson().toJson(lista))
            .apply()
    }

    fun obterProximaPergunta(context: Context): Pergunta? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lista = obterPerguntas(context).toMutableList()

        if (lista.isEmpty()) return null

        val pergunta = lista.removeAt(0)

        prefs.edit()
            .putString(KEY_QUESTIONS, Gson().toJson(lista))
            .apply()

        return pergunta
    }

    fun obterPerguntas(context: Context): List<Pergunta> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_QUESTIONS, null) ?: return emptyList()

        val type = object : TypeToken<List<Pergunta>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun quantidade(context: Context): Int {
        return obterPerguntas(context).size
    }

    fun limparCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_QUESTIONS)
            .apply()
    }
}