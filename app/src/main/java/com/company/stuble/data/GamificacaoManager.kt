package com.company.stuble.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class NivelInfo(
    val numero: Int,
    val titulo: String,
    val emoji: String,
    val xpAtual: Int,
    val xpInicio: Int,
    val xpProximo: Int?,
    val progressoPercentual: Int
)

data class Conquista(
    val id: String,
    val emoji: String,
    val titulo: String,
    val descricao: String,
    val desbloqueada: Boolean,
    val progresso: Int,
    val meta: Int,
    val recompensaXp: Int
)

data class DesafioDiario(
    val id: String,
    val titulo: String,
    val descricao: String,
    val emoji: String,
    val progresso: Int,
    val meta: Int,
    val recompensaXp: Int,
    val concluido: Boolean
)

data class DiaEstudo(
    val data: String,
    val rotulo: String,
    val estudou: Boolean,
    val quantidade: Int
)

data class EstatisticaArea(
    val area: String,
    val emoji: String,
    val respondidas: Int,
    val acertos: Int,
    val taxa: Int
)

data class ResultadoGamificacao(
    val xpGanho: Int,
    val xpTotal: Int,
    val nivelAnterior: NivelInfo,
    val nivelAtual: NivelInfo,
    val subiuNivel: Boolean,
    val novasConquistas: List<Conquista>,
    val desafioConcluidoAgora: Boolean
)

object GamificacaoManager {

    private const val PREFIXO_PREFS = "stuble_gamificacao_"

    private const val KEY_XP = "xp"
    private const val KEY_TOTAL_QUESTOES = "total_questoes"
    private const val KEY_TOTAL_ACERTOS = "total_acertos"
    private const val KEY_MISSOES = "missoes_concluidas"
    private const val KEY_MAIOR_SEQUENCIA = "maior_sequencia_acertos"
    private const val KEY_SEQUENCIA_ATUAL = "sequencia_acertos_atual"
    private const val KEY_CONQUISTAS = "conquistas_desbloqueadas"

    private const val XP_RESPONDER = 5
    private const val XP_ACERTAR = 10
    private const val XP_MISSAO = 50
    private const val XP_DESAFIO = 40

    private val areas = listOf(
        "Matemática",
        "Ciências da Natureza",
        "Ciências Humanas",
        "Linguagens e Códigos"
    )

    private fun uid(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFIXO_PREFS + uid(),
            Context.MODE_PRIVATE
        )

    private fun hoje(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun normalizarArea(areaRecebida: String): String {
        val area = areaRecebida.lowercase(Locale.ROOT)

        return when {
            "matem" in area || "exata" in area -> "Matemática"
            "natureza" in area || "física" in area ||
                "fisica" in area || "química" in area ||
                "quimica" in area || "biologia" in area ->
                "Ciências da Natureza"
            "humana" in area || "história" in area ||
                "historia" in area || "geografia" in area ||
                "filosofia" in area || "sociologia" in area ->
                "Ciências Humanas"
            else -> "Linguagens e Códigos"
        }
    }

    private fun chaveArea(prefixo: String, area: String): String =
        "${prefixo}_${normalizarArea(area)
            .lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace("ê", "e")
            .replace("á", "a")
            .replace("ó", "o")
            .replace("í", "i")
            .replace("ç", "c")}"

    @Synchronized
    fun registrarResposta(
        context: Context,
        acertou: Boolean,
        area: String,
        respondidasHojeDepois: Int,
        ehMissaoDiaria: Boolean
    ): ResultadoGamificacao {
        val preferencias = prefs(context)
        val xpAntes = preferencias.getInt(KEY_XP, 0)
        val nivelAntes = obterNivelPorXp(xpAntes)

        val areaNormalizada = normalizarArea(area)
        val totalAntes = preferencias.getInt(KEY_TOTAL_QUESTOES, 0)
        val acertosAntes = preferencias.getInt(KEY_TOTAL_ACERTOS, 0)

        val sequenciaAntes =
            preferencias.getInt(KEY_SEQUENCIA_ATUAL, 0)
        val novaSequencia = if (acertou) sequenciaAntes + 1 else 0
        val maiorSequenciaAntes =
            preferencias.getInt(KEY_MAIOR_SEQUENCIA, 0)

        var xpGanho = XP_RESPONDER + if (acertou) XP_ACERTAR else 0

        val editor = preferencias.edit()
            .putInt(KEY_TOTAL_QUESTOES, totalAntes + 1)
            .putInt(
                KEY_TOTAL_ACERTOS,
                acertosAntes + if (acertou) 1 else 0
            )
            .putInt(KEY_SEQUENCIA_ATUAL, novaSequencia)
            .putInt(
                KEY_MAIOR_SEQUENCIA,
                max(maiorSequenciaAntes, novaSequencia)
            )

        val chaveRespondidas = chaveArea("respondidas", areaNormalizada)
        val chaveAcertos = chaveArea("acertos", areaNormalizada)

        editor.putInt(
            chaveRespondidas,
            preferencias.getInt(chaveRespondidas, 0) + 1
        )

        if (acertou) {
            editor.putInt(
                chaveAcertos,
                preferencias.getInt(chaveAcertos, 0) + 1
            )
        }

        val chaveDia = "dia_${hoje()}"
        editor.putInt(
            chaveDia,
            preferencias.getInt(chaveDia, 0) + 1
        )

        val chaveMissaoPremiada = "missao_premiada_${hoje()}"
        if (
            ehMissaoDiaria &&
            respondidasHojeDepois >= 20 &&
            !preferencias.getBoolean(chaveMissaoPremiada, false)
        ) {
            xpGanho += XP_MISSAO
            editor
                .putBoolean(chaveMissaoPremiada, true)
                .putInt(
                    KEY_MISSOES,
                    preferencias.getInt(KEY_MISSOES, 0) + 1
                )
        }

        val desafioAntes = obterDesafioDiario(context)
        val progressoDesafio = when (desafioAntes.id) {
            "RESPONDER_10" -> respondidasHojeDepois
            "ACERTAR_7" -> getAcertosHoje(context) + if (acertou) 1 else 0
            else -> novaSequencia
        }.coerceAtMost(desafioAntes.meta)

        editor.putInt(
            "desafio_progresso_${hoje()}",
            progressoDesafio
        )

        var desafioConcluidoAgora = false
        val chaveDesafioPremiado = "desafio_premiado_${hoje()}"

        if (
            progressoDesafio >= desafioAntes.meta &&
            !preferencias.getBoolean(chaveDesafioPremiado, false)
        ) {
            xpGanho += XP_DESAFIO
            desafioConcluidoAgora = true
            editor.putBoolean(chaveDesafioPremiado, true)
        }

        if (acertou) {
            editor.putInt(
                "acertos_hoje_${hoje()}",
                getAcertosHoje(context) + 1
            )
        }

        editor.putInt(KEY_XP, xpAntes + xpGanho)
        editor.apply()

        val novasConquistas =
            verificarNovasConquistas(context).toMutableList()

        if (novasConquistas.isNotEmpty()) {
            val bonusConquistas =
                novasConquistas.sumOf { it.recompensaXp }

            xpGanho += bonusConquistas

            preferencias.edit()
                .putInt(KEY_XP, preferencias.getInt(KEY_XP, 0) + bonusConquistas)
                .apply()
        }

        val xpFinal = preferencias.getInt(KEY_XP, 0)
        val nivelAtual = obterNivelPorXp(xpFinal)

        return ResultadoGamificacao(
            xpGanho = xpGanho,
            xpTotal = xpFinal,
            nivelAnterior = nivelAntes,
            nivelAtual = nivelAtual,
            subiuNivel = nivelAtual.numero > nivelAntes.numero,
            novasConquistas = novasConquistas,
            desafioConcluidoAgora = desafioConcluidoAgora
        )
    }

    fun getXp(context: Context): Int =
        prefs(context).getInt(KEY_XP, 0)

    fun getTotalQuestoes(context: Context): Int =
        prefs(context).getInt(KEY_TOTAL_QUESTOES, 0)

    fun getTotalAcertos(context: Context): Int =
        prefs(context).getInt(KEY_TOTAL_ACERTOS, 0)

    fun getTaxaAcerto(context: Context): Int {
        val total = getTotalQuestoes(context)
        return if (total == 0) 0
        else ((getTotalAcertos(context) * 100f) / total).toInt()
    }

    fun getMissoesConcluidas(context: Context): Int =
        prefs(context).getInt(KEY_MISSOES, 0)

    fun getMaiorSequencia(context: Context): Int =
        prefs(context).getInt(KEY_MAIOR_SEQUENCIA, 0)

    private fun getAcertosHoje(context: Context): Int =
        prefs(context).getInt("acertos_hoje_${hoje()}", 0)

    fun obterNivel(context: Context): NivelInfo =
        obterNivelPorXp(getXp(context))

    private fun obterNivelPorXp(xp: Int): NivelInfo {
        val faixas = listOf(
            Triple(0, "Calouro", "🌱"),
            Triple(100, "Estudante", "📖"),
            Triple(300, "Vestibulando", "📝"),
            Triple(700, "Especialista", "🎓"),
            Triple(1500, "Mestre", "🚀"),
            Triple(3000, "Lenda Stuble", "👑")
        )

        val indice = faixas.indexOfLast { xp >= it.first }.coerceAtLeast(0)
        val atual = faixas[indice]
        val proximo = faixas.getOrNull(indice + 1)

        val progresso = if (proximo == null) {
            100
        } else {
            (((xp - atual.first).toFloat() /
                (proximo.first - atual.first)) * 100)
                .toInt()
                .coerceIn(0, 100)
        }

        return NivelInfo(
            numero = indice + 1,
            titulo = atual.second,
            emoji = atual.third,
            xpAtual = xp,
            xpInicio = atual.first,
            xpProximo = proximo?.first,
            progressoPercentual = progresso
        )
    }

    fun obterDesafioDiario(context: Context): DesafioDiario {
        val calendario = Calendar.getInstance()
        val indice = calendario.get(Calendar.DAY_OF_YEAR) % 3

        val base = when (indice) {
            0 -> DesafioDiario(
                "RESPONDER_10",
                "Aquecimento campeão",
                "Responda 10 questões hoje.",
                "⚡",
                0,
                10,
                XP_DESAFIO,
                false
            )
            1 -> DesafioDiario(
                "ACERTAR_7",
                "Mira certeira",
                "Acerte 7 questões hoje.",
                "🎯",
                0,
                7,
                XP_DESAFIO,
                false
            )
            else -> DesafioDiario(
                "SEQUENCIA_3",
                "Embalou!",
                "Acerte 3 questões seguidas.",
                "🔥",
                0,
                3,
                XP_DESAFIO,
                false
            )
        }

        val progresso = prefs(context)
            .getInt("desafio_progresso_${hoje()}", 0)
            .coerceAtMost(base.meta)

        return base.copy(
            progresso = progresso,
            concluido = progresso >= base.meta
        )
    }

    fun obterCalendarioSemanal(context: Context): List<DiaEstudo> {
        val formatoData = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val formatoDia = SimpleDateFormat("EEE", Locale("pt", "BR"))
        val resultado = mutableListOf<DiaEstudo>()

        for (offset in 6 downTo 0) {
            val calendario = Calendar.getInstance()
            calendario.add(Calendar.DAY_OF_YEAR, -offset)

            val data = formatoData.format(calendario.time)
            val quantidade = prefs(context).getInt("dia_$data", 0)
            val rotulo = formatoDia.format(calendario.time)
                .replaceFirstChar { it.uppercase() }
                .take(3)

            resultado += DiaEstudo(
                data = data,
                rotulo = rotulo,
                estudou = quantidade > 0,
                quantidade = quantidade
            )
        }

        return resultado
    }

    fun obterEstatisticasPorArea(
        context: Context
    ): List<EstatisticaArea> {
        return areas.map { area ->
            val respondidas =
                prefs(context).getInt(chaveArea("respondidas", area), 0)
            val acertos =
                prefs(context).getInt(chaveArea("acertos", area), 0)
            val taxa = if (respondidas == 0) 0
            else ((acertos * 100f) / respondidas).toInt()

            EstatisticaArea(
                area = area,
                emoji = when (area) {
                    "Matemática" -> "🧮"
                    "Ciências da Natureza" -> "🧬"
                    "Ciências Humanas" -> "🌎"
                    else -> "📚"
                },
                respondidas = respondidas,
                acertos = acertos,
                taxa = taxa
            )
        }
    }

    fun obterConquistas(context: Context): List<Conquista> {
        val desbloqueadas = prefs(context)
            .getStringSet(KEY_CONQUISTAS, emptySet())
            .orEmpty()

        val total = getTotalQuestoes(context)
        val taxa = getTaxaAcerto(context)
        val missoes = getMissoesConcluidas(context)
        val maiorSequencia = getMaiorSequencia(context)
        val diasEstudados =
            obterCalendarioSemanal(context).count { it.estudou }

        val bases = listOf(
            Conquista(
                "PRIMEIRA_QUESTAO", "✅", "Primeiros passos",
                "Responda sua primeira questão.",
                false, total.coerceAtMost(1), 1, 20
            ),
            Conquista(
                "VINTE_QUESTOES", "🔥", "Missão cumprida",
                "Conclua 20 questões.",
                false, total.coerceAtMost(20), 20, 30
            ),
            Conquista(
                "CEM_QUESTOES", "📚", "Centenário",
                "Responda 100 questões.",
                false, total.coerceAtMost(100), 100, 50
            ),
            Conquista(
                "PRECISAO_80", "🎯", "Mira afiada",
                "Alcance 80% de acerto com pelo menos 20 questões.",
                false,
                if (total >= 20) taxa.coerceAtMost(80) else 0,
                80, 50
            ),
            Conquista(
                "SEQUENCIA_5", "⚡", "Imparável",
                "Acerte 5 questões seguidas.",
                false, maiorSequencia.coerceAtMost(5), 5, 40
            ),
            Conquista(
                "TRES_MISSOES", "🏆", "Rotina criada",
                "Conclua 3 missões diárias.",
                false, missoes.coerceAtMost(3), 3, 50
            ),
            Conquista(
                "SEMANA_ATIVA", "🗓️", "Semana ativa",
                "Estude em 5 dias da semana.",
                false, diasEstudados.coerceAtMost(5), 5, 60
            )
        )

        return bases.map {
            it.copy(desbloqueada = it.id in desbloqueadas)
        }
    }

    private fun verificarNovasConquistas(
        context: Context
    ): List<Conquista> {
        val preferencias = prefs(context)
        val antigas = preferencias
            .getStringSet(KEY_CONQUISTAS, emptySet())
            .orEmpty()
            .toMutableSet()

        val candidatas = obterConquistas(context)
            .filter {
                !it.desbloqueada &&
                    it.progresso >= it.meta
            }

        if (candidatas.isNotEmpty()) {
            antigas.addAll(candidatas.map { it.id })
            preferencias.edit()
                .putStringSet(KEY_CONQUISTAS, antigas)
                .apply()
        }

        return candidatas.map { it.copy(desbloqueada = true) }
    }

    fun resetarParaTestes(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
