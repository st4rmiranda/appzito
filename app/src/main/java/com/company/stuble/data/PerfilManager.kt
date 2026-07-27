package com.company.stuble.data

import android.content.Context
import com.company.stuble.model.PerfilEstudante
import com.google.firebase.auth.FirebaseAuth

object PerfilManager {

    private const val PREFIXO_PREFS = "perfil_estudante_"

    private const val KEY_ANO = "ano_escolar"
    private const val KEY_AREA = "area_interesse"
    private const val KEY_CURSO = "curso_desejado"
    private const val KEY_OBJETIVO = "objetivo"
    private const val KEY_DIFICULDADE = "dificuldade_preferida"
    private const val KEY_MATERIAS = "materias_dificuldade"
    private const val KEY_CONCLUIDO = "onboarding_concluido"

    private fun identificadorUsuario(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFIXO_PREFS + identificadorUsuario(),
            Context.MODE_PRIVATE
        )

    fun salvarPerfil(context: Context, perfil: PerfilEstudante) {
        prefs(context).edit()
            .putString(KEY_ANO, perfil.anoEscolar)
            .putString(KEY_AREA, perfil.areaInteresse)
            .putString(KEY_CURSO, perfil.cursoDesejado)
            .putString(KEY_OBJETIVO, perfil.objetivo)
            .putString(KEY_DIFICULDADE, perfil.dificuldadePreferida)
            .putStringSet(KEY_MATERIAS, perfil.materiasDificuldade.toSet())
            .putBoolean(KEY_CONCLUIDO, true)
            .apply()
    }

    fun carregarPerfil(context: Context): PerfilEstudante {
        val preferencias = prefs(context)

        return PerfilEstudante(
            anoEscolar = preferencias.getString(KEY_ANO, "").orEmpty(),
            areaInteresse = preferencias.getString(KEY_AREA, "").orEmpty(),
            cursoDesejado = preferencias.getString(KEY_CURSO, "").orEmpty(),
            objetivo = preferencias.getString(KEY_OBJETIVO, "").orEmpty(),
            dificuldadePreferida = preferencias
                .getString(KEY_DIFICULDADE, "Intermediário")
                .orEmpty()
                .ifBlank { "Intermediário" },
            materiasDificuldade = preferencias
                .getStringSet(KEY_MATERIAS, emptySet())
                ?.toSet()
                ?: emptySet()
        )
    }

    fun onboardingConcluido(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_CONCLUIDO, false)
    }

    fun limparPerfilDoUsuarioAtual(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
