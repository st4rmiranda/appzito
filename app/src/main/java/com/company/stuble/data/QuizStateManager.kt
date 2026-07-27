package com.company.stuble.data

import android.content.Context
import com.company.stuble.model.Pergunta
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuizState(
    val data: String,
    val pergunta: Pergunta,
    val filtro: String?,
    val treinoLivre: Boolean
)

object QuizStateManager {

    private const val PREFIXO_PREFS = "quiz_state_"
    private const val KEY_STATE = "state"
    private val gson = Gson()

    private fun uid(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFIXO_PREFS + uid(),
            Context.MODE_PRIVATE
        )

    private fun hoje(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun salvar(
        context: Context,
        pergunta: Pergunta,
        filtro: String?,
        treinoLivre: Boolean
    ) {
        val estado = QuizState(
            data = hoje(),
            pergunta = pergunta,
            filtro = filtro,
            treinoLivre = treinoLivre
        )

        prefs(context).edit()
            .putString(KEY_STATE, gson.toJson(estado))
            .apply()
    }

    fun restaurar(
        context: Context,
        filtro: String?,
        treinoLivre: Boolean
    ): Pergunta? {
        val json = prefs(context)
            .getString(KEY_STATE, null)
            ?: return null

        return try {
            val estado = gson.fromJson(json, QuizState::class.java)

            val mesmoFiltro =
                estado.filtro.orEmpty() == filtro.orEmpty()

            if (
                estado.data == hoje() &&
                estado.treinoLivre == treinoLivre &&
                mesmoFiltro
            ) {
                estado.pergunta
            } else {
                limpar(context)
                null
            }
        } catch (_: Exception) {
            limpar(context)
            null
        }
    }

    fun limpar(context: Context) {
        prefs(context).edit()
            .remove(KEY_STATE)
            .apply()
    }
}
