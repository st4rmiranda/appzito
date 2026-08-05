package com.company.stuble.data

import android.content.Context
import android.util.Log
import com.company.stuble.model.Pergunta
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.util.Locale

object RevisaoErrosManager {

    private const val TAG = "RevisaoErrosManager"
    private const val PREFIXO_PREFS = "revisao_erros_"
    private const val KEY_ERROS = "questoes_erradas"
    private const val LIMITE = 20

    private val gson = Gson()

    private fun uid(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFIXO_PREFS + uid(), Context.MODE_PRIVATE)

    @Synchronized
    fun registrarErro(context: Context, pergunta: Pergunta) {
        if (!perguntaValida(pergunta)) {
            Log.w(TAG, "Pergunta inválida ignorada.")
            return
        }

        val lista = obterErros(context).toMutableList()

        val repetida = lista.any {
            normalizar(it.pergunta) == normalizar(pergunta.pergunta)
        }

        if (repetida) {
            return
        }

        lista.add(pergunta)

        while (lista.size > LIMITE) {
            lista.removeAt(0)
        }

        salvarLista(context, lista)
    }

    fun obterErros(context: Context): List<Pergunta> {
        val json = prefs(context).getString(KEY_ERROS, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<Pergunta>>() {}.type

            gson.fromJson<List<Pergunta>>(json, type)
                ?.filter(::perguntaValida)
                ?: emptyList()
        } catch (erro: JsonSyntaxException) {
            Log.e(TAG, "Cache de erros inválido. Será limpo.", erro)
            limparTudo(context)
            emptyList()
        } catch (erro: Exception) {
            Log.e(TAG, "Erro ao ler as questões erradas.", erro)
            emptyList()
        }
    }

    @Synchronized
    fun removerErro(context: Context, pergunta: Pergunta) {
        val lista = obterErros(context).filterNot {
            normalizar(it.pergunta) == normalizar(pergunta.pergunta)
        }

        salvarLista(context, lista)
    }

    fun quantidade(context: Context): Int = obterErros(context).size

    fun limparTudo(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun salvarLista(context: Context, lista: List<Pergunta>) {
        prefs(context)
            .edit()
            .putString(KEY_ERROS, gson.toJson(lista))
            .apply()
    }

    private fun perguntaValida(pergunta: Pergunta): Boolean {
        return pergunta.pergunta.isNotBlank() &&
            pergunta.opcoes.size == 4 &&
            pergunta.opcoes.none { it.isBlank() } &&
            pergunta.correta in 0..3 &&
            pergunta.explicacao.isNotBlank()
    }

    private fun normalizar(texto: String): String {
        return texto
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }
}
