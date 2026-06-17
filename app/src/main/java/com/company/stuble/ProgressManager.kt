package com.company.stuble

import android.content.Context

object ProgressManager {

    private const val PREFS_NAME = "StublePrefs"
    private const val KEY_QUESTOES = "PERGUNTAS_RESPONDIDAS_HOJE"

    fun adicionarQuestaoRespondida(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val atual = prefs.getInt(KEY_QUESTOES, 0)

        if (atual < 20) {
            prefs.edit()
                .putInt(KEY_QUESTOES, atual + 1)
                .apply()
        }
    }

    fun getQuestoesRespondidas(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_QUESTOES, 0)
    }

    fun getPercentual(context: Context): Int {
        return ((getQuestoesRespondidas(context).toFloat() / 20f) * 100f).toInt()
    }

    fun resetar(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_QUESTOES, 0)
            .apply()
    }
}