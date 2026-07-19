package com.company.stuble

import android.content.Context
import android.util.Log
import com.company.stuble.model.Pergunta
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QuestionCacheManager {

    private const val TAG = "QuestionCacheManager"
    private const val PREFIXO_PREFS = "question_cache_"
    private const val KEY_QUESTIONS = "cached_questions"
    private const val KEY_USED_DATE = "used_date"
    private const val KEY_USED_QUESTIONS = "used_questions"
    private const val LIMITE_CACHE = 30

    private val gson = Gson()

    private fun uid(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFIXO_PREFS + uid(),
            Context.MODE_PRIVATE
        )

    private fun hoje(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())
    }

    @Synchronized
    fun salvarPergunta(
        context: Context,
        pergunta: Pergunta
    ) {
        if (!perguntaValida(pergunta)) {
            Log.w(TAG, "Pergunta inválida ignorada.")
            return
        }

        val lista = obterPerguntas(context).toMutableList()

        val repetida = lista.any {
            normalizar(it.pergunta) ==
                    normalizar(pergunta.pergunta)
        }

        if (
            repetida ||
            foiUsadaHoje(context, pergunta)
        ) {
            Log.d(
                TAG,
                "Pergunta repetida ignorada."
            )
            return
        }

        lista.add(pergunta)

        while (lista.size > LIMITE_CACHE) {
            lista.removeAt(0)
        }

        salvarLista(context, lista)
    }

    @Synchronized
    fun obterProximaPergunta(
        context: Context,
        areaPreferida: String? = null
    ): Pergunta? {
        normalizarHistoricoDoDia(context)

        val lista =
            obterPerguntas(context).toMutableList()

        if (lista.isEmpty()) {
            return null
        }

        val indice = if (
            areaPreferida.isNullOrBlank()
        ) {
            0
        } else {
            lista.indexOfFirst {
                normalizarArea(it.area) ==
                        normalizarArea(areaPreferida)
            }.takeIf { it >= 0 } ?: 0
        }

        val pergunta = lista.removeAt(indice)

        salvarLista(context, lista)
        marcarComoUsada(context, pergunta)

        return pergunta
    }

    fun obterPerguntas(
        context: Context
    ): List<Pergunta> {
        val json = prefs(context)
            .getString(KEY_QUESTIONS, null)
            ?: return emptyList()

        return try {
            val type = object :
                TypeToken<List<Pergunta>>() {}.type

            gson.fromJson<List<Pergunta>>(
                json,
                type
            )
                ?.filter(::perguntaValida)
                ?: emptyList()

        } catch (erro: JsonSyntaxException) {
            Log.e(
                TAG,
                "Cache inválido. O cache será limpo.",
                erro
            )

            limparCache(context)
            emptyList()

        } catch (erro: Exception) {
            Log.e(
                TAG,
                "Erro ao ler o cache.",
                erro
            )

            emptyList()
        }
    }

    fun quantidade(
        context: Context,
        areaPreferida: String? = null
    ): Int {
        val lista = obterPerguntas(context)

        return if (
            areaPreferida.isNullOrBlank()
        ) {
            lista.size
        } else {
            lista.count {
                normalizarArea(it.area) ==
                        normalizarArea(areaPreferida)
            }
        }
    }

    fun possuiPerguntas(
        context: Context,
        areaPreferida: String? = null
    ): Boolean {
        return quantidade(
            context,
            areaPreferida
        ) > 0
    }

    fun foiUsadaHoje(
        context: Context,
        pergunta: Pergunta
    ): Boolean {
        normalizarHistoricoDoDia(context)

        return normalizar(pergunta.pergunta) in
                obterUsadas(context)
    }

    @Synchronized
    fun marcarComoUsada(
        context: Context,
        pergunta: Pergunta
    ) {
        normalizarHistoricoDoDia(context)

        val usadas =
            obterUsadas(context).toMutableSet()

        usadas.add(
            normalizar(pergunta.pergunta)
        )

        prefs(context)
            .edit()
            .putStringSet(
                KEY_USED_QUESTIONS,
                usadas
            )
            .apply()
    }

    fun limparCache(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_QUESTIONS)
            .apply()
    }

    fun limparTudo(context: Context) {
        prefs(context)
            .edit()
            .clear()
            .apply()
    }

    private fun obterUsadas(
        context: Context
    ): Set<String> {
        return prefs(context)
            .getStringSet(
                KEY_USED_QUESTIONS,
                emptySet()
            )
            .orEmpty()
    }

    private fun normalizarHistoricoDoDia(
        context: Context
    ) {
        val preferencias = prefs(context)

        val data = preferencias.getString(
            KEY_USED_DATE,
            null
        )

        if (data != hoje()) {
            preferencias
                .edit()
                .putString(
                    KEY_USED_DATE,
                    hoje()
                )
                .remove(KEY_USED_QUESTIONS)
                .apply()
        }
    }

    private fun salvarLista(
        context: Context,
        lista: List<Pergunta>
    ) {
        prefs(context)
            .edit()
            .putString(
                KEY_QUESTIONS,
                gson.toJson(lista)
            )
            .apply()
    }

    private fun perguntaValida(
        pergunta: Pergunta
    ): Boolean {
        return pergunta.pergunta.isNotBlank() &&
                pergunta.opcoes.size == 4 &&
                pergunta.opcoes.none { it.isBlank() } &&
                pergunta.correta in 0..3 &&
                pergunta.explicacao.isNotBlank()
    }

    private fun normalizar(
        texto: String
    ): String {
        return texto
            .trim()
            .lowercase(Locale.ROOT)
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun normalizarArea(
        texto: String
    ): String {
        return texto
            .trim()
            .lowercase(Locale.ROOT)
    }
}