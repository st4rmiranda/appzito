package com.company.stuble.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ProgressManager {

    private const val TOTAL_DIARIO = 20
    private const val PREFIXO_PREFS = "progresso_estudos_"

    private const val KEY_DATA_CONTAGEM = "data_contagem"
    private const val KEY_RESPONDIDAS_HOJE = "respondidas_hoje"
    private const val KEY_OFENSIVA = "ofensiva"
    private const val KEY_ULTIMA_CONCLUSAO = "ultima_conclusao"

    private fun identificadorUsuario(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFIXO_PREFS + identificadorUsuario(),
            Context.MODE_PRIVATE
        )

    private fun formatarData(data: Date): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(data)
    }

    private fun hoje(): String = formatarData(Date())

    private fun ontem(): String {
        val calendario = Calendar.getInstance()
        calendario.add(Calendar.DAY_OF_YEAR, -1)
        return formatarData(calendario.time)
    }

    private fun normalizarDia(context: Context) {
        val preferencias = prefs(context)
        val dataSalva = preferencias.getString(KEY_DATA_CONTAGEM, null)

        if (dataSalva != hoje()) {
            preferencias.edit()
                .putString(KEY_DATA_CONTAGEM, hoje())
                .putInt(KEY_RESPONDIDAS_HOJE, 0)
                .apply()
        }
    }

    @Synchronized
    fun getQuestoesRespondidasHoje(context: Context): Int {
        normalizarDia(context)
        return prefs(context).getInt(KEY_RESPONDIDAS_HOJE, 0)
    }

    /**
     * Registra uma resposta e devolve a nova quantidade respondida no dia.
     * Ao chegar a 20, atualiza a ofensiva apenas uma vez.
     */
    @Synchronized
    fun adicionarQuestaoRespondida(context: Context): Int {
        normalizarDia(context)

        val preferencias = prefs(context)
        val quantidadeAtual = preferencias.getInt(KEY_RESPONDIDAS_HOJE, 0)

        if (quantidadeAtual >= TOTAL_DIARIO) {
            return TOTAL_DIARIO
        }

        val novaQuantidade = quantidadeAtual + 1
        val editor = preferencias.edit()
            .putInt(KEY_RESPONDIDAS_HOJE, novaQuantidade)

        if (novaQuantidade == TOTAL_DIARIO) {
            val ultimaConclusao =
                preferencias.getString(KEY_ULTIMA_CONCLUSAO, null)
            val ofensivaAtual = preferencias.getInt(KEY_OFENSIVA, 0)

            val novaOfensiva = when (ultimaConclusao) {
                hoje() -> ofensivaAtual
                ontem() -> ofensivaAtual + 1
                else -> 1
            }

            editor
                .putInt(KEY_OFENSIVA, novaOfensiva)
                .putString(KEY_ULTIMA_CONCLUSAO, hoje())
        }

        editor.apply()
        return novaQuantidade
    }

    @Synchronized
    fun getOfensiva(context: Context): Int {
        val preferencias = prefs(context)
        val ultimaConclusao =
            preferencias.getString(KEY_ULTIMA_CONCLUSAO, null)

        if (ultimaConclusao != hoje() && ultimaConclusao != ontem()) {
            preferencias.edit().putInt(KEY_OFENSIVA, 0).apply()
            return 0
        }

        return preferencias.getInt(KEY_OFENSIVA, 0)
    }

    fun sequenciaConcluidaHoje(context: Context): Boolean {
        return getQuestoesRespondidasHoje(context) >= TOTAL_DIARIO
    }

    fun resetarParaTestes(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
