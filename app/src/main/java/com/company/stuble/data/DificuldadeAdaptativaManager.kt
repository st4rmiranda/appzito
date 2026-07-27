package com.company.stuble.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

object DificuldadeAdaptativaManager {

    private const val PREFIXO_PREFS = "dificuldade_adaptativa_"

    private const val KEY_NIVEL = "nivel_atual"
    private const val KEY_ACERTOS_SEGUIDOS = "acertos_seguidos"
    private const val KEY_ERROS_SEGUIDOS = "erros_seguidos"

    private fun identificadorUsuario(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFIXO_PREFS + identificadorUsuario(),
            Context.MODE_PRIVATE
        )

    fun obterDificuldadeAtual(
        context: Context,
        dificuldadePreferida: String
    ): String {
        if (dificuldadePreferida != "Adaptativo") {
            return dificuldadePreferida
        }

        return prefs(context).getString(KEY_NIVEL, "Intermediário")
            ?: "Intermediário"
    }

    /**
     * No modo adaptativo:
     * - 3 acertos consecutivos sobem um nível;
     * - 2 erros consecutivos descem um nível.
     */
    fun registrarResposta(
        context: Context,
        acertou: Boolean,
        dificuldadePreferida: String
    ) {
        if (dificuldadePreferida != "Adaptativo") return

        val preferencias = prefs(context)
        var nivel = preferencias.getString(KEY_NIVEL, "Intermediário")
            ?: "Intermediário"
        var acertosSeguidos = preferencias.getInt(KEY_ACERTOS_SEGUIDOS, 0)
        var errosSeguidos = preferencias.getInt(KEY_ERROS_SEGUIDOS, 0)

        if (acertou) {
            acertosSeguidos++
            errosSeguidos = 0

            if (acertosSeguidos >= 3) {
                nivel = when (nivel) {
                    "Básico" -> "Intermediário"
                    "Intermediário" -> "Avançado"
                    else -> "Avançado"
                }
                acertosSeguidos = 0
            }
        } else {
            errosSeguidos++
            acertosSeguidos = 0

            if (errosSeguidos >= 2) {
                nivel = when (nivel) {
                    "Avançado" -> "Intermediário"
                    "Intermediário" -> "Básico"
                    else -> "Básico"
                }
                errosSeguidos = 0
            }
        }

        preferencias.edit()
            .putString(KEY_NIVEL, nivel)
            .putInt(KEY_ACERTOS_SEGUIDOS, acertosSeguidos)
            .putInt(KEY_ERROS_SEGUIDOS, errosSeguidos)
            .apply()
    }

    fun resetar(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
