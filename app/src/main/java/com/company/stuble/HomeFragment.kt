package com.company.stuble

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.company.stuble.data.Conquista
import com.company.stuble.data.GamificacaoManager
import com.company.stuble.data.ProgressManager
import com.company.stuble.data.RevisaoErrosManager
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.max

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        atualizarNome(view)

        view.findViewById<Button>(R.id.btnStartQuestions)
            .setOnClickListener {
                startActivity(
                    Intent(requireContext(), LoadingActivity::class.java)
                )
            }

        view.findViewById<Button>(R.id.btnRevisarErros)
            .setOnClickListener {
                startActivity(
                    Intent(requireContext(), RevisaoErrosActivity::class.java)
                )
            }
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            atualizarNome(it)
            atualizarTela(it)
        }
    }

    private fun atualizarNome(view: View) {
        val nome = FirebaseAuth.getInstance()
            .currentUser
            ?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: "Estudante"

        view.findViewById<TextView>(R.id.txtNomeUsuario).text =
            "Bora estudar, $nome 🔥"
    }

    private fun atualizarTela(view: View) {
        val context = requireContext()

        val respondidasHoje =
            ProgressManager.getQuestoesRespondidasHoje(context)

        val percentual =
            ((respondidasHoje / 20f) * 100)
                .toInt()
                .coerceIn(0, 100)

        val barraMissao =
            view.findViewById<ProgressBar>(R.id.homeProgressBar)

        ObjectAnimator.ofInt(
            barraMissao,
            "progress",
            barraMissao.progress,
            percentual
        ).apply {
            duration = 650
            start()
        }

        view.findViewById<TextView>(R.id.txtPercentual).text =
            "$percentual%"

        view.findViewById<TextView>(R.id.txtMetaDiaria).text =
            "$respondidasHoje de 20 questões concluídas hoje"

        view.findViewById<TextView>(R.id.txtStreak).text =
            ProgressManager.getOfensiva(context).toString()

        view.findViewById<TextView>(R.id.txtAcertos).text =
            "${GamificacaoManager.getTaxaAcerto(context)}%"

        view.findViewById<TextView>(R.id.txtTotalQuestoes).text =
            GamificacaoManager.getTotalQuestoes(context).toString()

        view.findViewById<TextView>(R.id.txtMissoes).text =
            GamificacaoManager.getMissoesConcluidas(context).toString()

        atualizarNivel(view)
        atualizarDesafio(view)
        atualizarCalendario(view)
        atualizarEstatisticas(view)
        atualizarConquistas(view)
        atualizarCardRevisaoErros(view)
    }

    private fun atualizarNivel(view: View) {
        val nivel = GamificacaoManager.obterNivel(requireContext())

        view.findViewById<TextView>(R.id.txtNivelEstudante).text =
            "${nivel.emoji} ${nivel.titulo}"

        view.findViewById<TextView>(R.id.txtNivelDescricao).text =
            "Nível ${nivel.numero} • ${nivel.xpAtual} XP acumulados"

        val barra = view.findViewById<ProgressBar>(R.id.progressNivel)
        barra.max = 100

        ObjectAnimator.ofInt(
            barra,
            "progress",
            barra.progress,
            nivel.progressoPercentual
        ).apply {
            duration = 650
            start()
        }

        view.findViewById<TextView>(R.id.txtProximoNivel).text =
            nivel.xpProximo?.let {
                "${it - nivel.xpAtual} XP para o próximo nível"
            } ?: "Você chegou ao título máximo! 👑"
    }

    private fun atualizarDesafio(view: View) {
        val desafio =
            GamificacaoManager.obterDesafioDiario(requireContext())

        view.findViewById<TextView>(R.id.txtDesafioEmoji).text =
            desafio.emoji

        view.findViewById<TextView>(R.id.txtDesafioTitulo).text =
            desafio.titulo

        view.findViewById<TextView>(R.id.txtDesafioDescricao).text =
            desafio.descricao

        view.findViewById<TextView>(R.id.txtDesafioRecompensa).text =
            if (desafio.concluido) {
                "Concluído! +${desafio.recompensaXp} XP recebidos ✅"
            } else {
                "Recompensa: +${desafio.recompensaXp} XP"
            }

        view.findViewById<TextView>(R.id.txtDesafioProgresso).text =
            "${desafio.progresso} / ${desafio.meta}"

        val barra =
            view.findViewById<ProgressBar>(R.id.progressDesafio)

        barra.max = desafio.meta
        barra.progress = desafio.progresso
    }

    private fun atualizarCalendario(view: View) {
        val container =
            view.findViewById<LinearLayout>(R.id.containerCalendario)

        container.removeAllViews()

        GamificacaoManager
            .obterCalendarioSemanal(requireContext())
            .forEach { dia ->
                val coluna = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val bolinha = TextView(requireContext()).apply {
                    text = if (dia.estudou) "✓" else "•"
                    gravity = Gravity.CENTER
                    textSize = if (dia.estudou) 17f else 24f
                    setTextColor(
                        Color.parseColor(
                            if (dia.estudou) "#FFFFFF" else "#94A3B8"
                        )
                    )
                    setBackgroundResource(
                        if (dia.estudou) {
                            R.drawable.bg_calendar_active
                        } else {
                            R.drawable.bg_calendar_inactive
                        }
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                    )
                }

                val rotulo = TextView(requireContext()).apply {
                    text = dia.rotulo
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, dp(6), 0, 0)
                }

                coluna.addView(bolinha)
                coluna.addView(rotulo)
                container.addView(coluna)
            }
    }

    private fun atualizarEstatisticas(view: View) {
        val container =
            view.findViewById<LinearLayout>(R.id.containerEstatisticasAreas)

        container.removeAllViews()

        GamificacaoManager
            .obterEstatisticasPorArea(requireContext())
            .forEach { estatistica ->
                val item = layoutInflater.inflate(
                    R.layout.item_estatistica_area,
                    container,
                    false
                )

                item.findViewById<TextView>(R.id.txtAreaEmoji).text =
                    estatistica.emoji

                item.findViewById<TextView>(R.id.txtAreaNome).text =
                    estatistica.area

                item.findViewById<TextView>(R.id.txtAreaResumo).text =
                    "${estatistica.respondidas} questões • " +
                        "${estatistica.acertos} acertos"

                item.findViewById<TextView>(R.id.txtAreaTaxa).text =
                    "${estatistica.taxa}%"

                item.findViewById<ProgressBar>(R.id.progressArea).apply {
                    max = 100
                    progress = estatistica.taxa
                }

                container.addView(item)
            }
    }

    private fun atualizarConquistas(view: View) {
        val conquistas =
            GamificacaoManager.obterConquistas(requireContext())

        val desbloqueadas = conquistas.count { it.desbloqueada }

        view.findViewById<TextView>(R.id.txtResumoConquistas).text =
            "$desbloqueadas de ${conquistas.size} desbloqueadas"

        val container =
            view.findViewById<LinearLayout>(R.id.containerConquistas)

        container.removeAllViews()

        conquistas.take(4).forEach { conquista ->
            container.addView(criarItemConquista(conquista))
        }
    }

    private fun atualizarCardRevisaoErros(view: View) {
        val quantidade = RevisaoErrosManager.quantidade(requireContext())
        val card = view.findViewById<View>(R.id.cardRevisaoErros)

        card.isVisible = quantidade > 0

        if (quantidade > 0) {
            view.findViewById<TextView>(R.id.txtRevisaoErrosResumo).text =
                if (quantidade == 1) {
                    "1 questão para revisar"
                } else {
                    "$quantidade questões para revisar"
                }
        }
    }

    private fun criarItemConquista(conquista: Conquista): View {
        val item = layoutInflater.inflate(
            R.layout.item_conquista_home,
            null,
            false
        )

        item.alpha = if (conquista.desbloqueada) 1f else 0.62f

        item.findViewById<TextView>(R.id.txtConquistaEmoji).text =
            conquista.emoji

        item.findViewById<TextView>(R.id.txtConquistaTitulo).text =
            conquista.titulo

        item.findViewById<TextView>(R.id.txtConquistaDescricao).text =
            conquista.descricao

        item.findViewById<TextView>(R.id.txtConquistaStatus).text =
            if (conquista.desbloqueada) {
                "DESBLOQUEADA • +${conquista.recompensaXp} XP"
            } else {
                "${conquista.progresso} / ${conquista.meta}"
            }

        item.findViewById<ProgressBar>(R.id.progressConquista).apply {
            max = max(1, conquista.meta)
            progress = conquista.progresso.coerceAtMost(conquista.meta)
        }

        return item
    }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()
}
