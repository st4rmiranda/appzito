package com.company.stuble

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProgressManagerAntigo {

    private const val PREFS = "stuble_progress"

    private const val TOTAL_QUESTOES = "TOTAL_QUESTOES"
    private const val TOTAL_ACERTOS = "TOTAL_ACERTOS"
    private const val QUESTOES_HOJE = "QUESTOES_HOJE"
    private const val ULTIMA_DATA = "ULTIMA_DATA"

    fun adicionarQuestaoRespondida(context: Context, acertou: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val hoje = dataAtual()
        val ultimaData = prefs.getString(ULTIMA_DATA, "")

        var questoesHoje = prefs.getInt(QUESTOES_HOJE, 0)

        if (ultimaData != hoje) {
            questoesHoje = 0
        }

        val totalQuestoes = prefs.getInt(TOTAL_QUESTOES, 0)
        val totalAcertos = prefs.getInt(TOTAL_ACERTOS, 0)

        prefs.edit()
            .putString(ULTIMA_DATA, hoje)
            .putInt(QUESTOES_HOJE, questoesHoje + 1)
            .putInt(TOTAL_QUESTOES, totalQuestoes + 1)
            .putInt(TOTAL_ACERTOS, if (acertou) totalAcertos + 1 else totalAcertos)
            .apply()
    }

    fun getTotalQuestoes(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(TOTAL_QUESTOES, 0)
    }

    fun getTotalAcertos(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(TOTAL_ACERTOS, 0)
    }

    fun getTaxaAcerto(context: Context): Int {
        val total = getTotalQuestoes(context)
        val acertos = getTotalAcertos(context)

        return if (total == 0) {
            0
        } else {
            ((acertos.toFloat() / total.toFloat()) * 100).toInt()
        }
    }

    fun getQuestoesHoje(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hoje = dataAtual()
        val ultimaData = prefs.getString(ULTIMA_DATA, "")

        return if (ultimaData == hoje) {
            prefs.getInt(QUESTOES_HOJE, 0)
        } else {
            0
        }
    }

    private fun dataAtual(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}