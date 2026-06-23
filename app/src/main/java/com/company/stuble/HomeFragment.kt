package com.company.stuble

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth

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

        val txtNome = view.findViewById<TextView>(R.id.txtNomeUsuario)
        val user = FirebaseAuth.getInstance().currentUser
        val nomeUsuario = user?.displayName ?: "Estudante"

        txtNome.text = "Bora estudar, $nomeUsuario 🔥"

        val btnIniciar = view.findViewById<Button>(R.id.btnStartQuestions)

        btnIniciar.setOnClickListener {
            val intent = Intent(requireContext(), QuizActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        atualizarProgresso()
    }

    private fun atualizarProgresso() {
        val context = requireContext()
        val view = requireView()

        val respondidasHoje = ProgressManager.getQuestoesHoje(context)
        val totalQuestoes = ProgressManager.getTotalQuestoes(context)
        val taxaAcerto = ProgressManager.getTaxaAcerto(context)

        val percentualMissao = ((respondidasHoje.toFloat() / 20f) * 100).toInt()
        val percentualSeguro = percentualMissao.coerceIn(0, 100)

        val homeProgressBar = view.findViewById<ProgressBar>(R.id.homeProgressBar)
        val txtPercentual = view.findViewById<TextView>(R.id.txtPercentual)
        val txtMeta = view.findViewById<TextView>(R.id.txtMetaDiaria)

        ObjectAnimator.ofInt(
            homeProgressBar,
            "progress",
            homeProgressBar.progress,
            percentualSeguro
        ).apply {
            duration = 700
            start()
        }

        txtPercentual.text = "$percentualSeguro%"
        txtMeta.text = "$respondidasHoje de 20 questões concluídas hoje"

        view.findViewById<TextView>(R.id.txtTotalQuestoes).text =
            totalQuestoes.toString()

        view.findViewById<TextView>(R.id.txtAcertos).text =
            "$taxaAcerto%"

        view.findViewById<TextView>(R.id.txtStreak).text =
            "${ProgressManager.getQuestoesHoje(context)}"

        view.findViewById<TextView>(R.id.txtMissoes).text =
            (totalQuestoes / 20).toString()

        atualizarNivel(view, totalQuestoes)
        atualizarConquistas(view, totalQuestoes, taxaAcerto)
    }

    private fun atualizarNivel(view: View, totalQuestoes: Int) {
        val txtNivel = view.findViewById<TextView>(R.id.txtNivelEstudante)
        val txtNivelDescricao = view.findViewById<TextView>(R.id.txtNivelDescricao)
        val txtProximoNivel = view.findViewById<TextView>(R.id.txtProximoNivel)
        val progressNivel = view.findViewById<ProgressBar>(R.id.progressNivel)

        when {
            totalQuestoes < 100 -> {
                txtNivel.text = "🥉 Explorador Stuble"
                txtNivelDescricao.text = "Você está começando sua jornada de estudos."
                progressNivel.max = 100
                progressNivel.progress = totalQuestoes
                txtProximoNivel.text = "$totalQuestoes / 100 questões para o próximo nível"
            }

            totalQuestoes < 500 -> {
                txtNivel.text = "🥈 Estudante Dedicado"
                txtNivelDescricao.text = "Você já criou uma rotina consistente de estudos."
                progressNivel.max = 500
                progressNivel.progress = totalQuestoes
                txtProximoNivel.text = "$totalQuestoes / 500 questões para o próximo nível"
            }

            totalQuestoes < 1000 -> {
                txtNivel.text = "🥇 Vestibulando Elite"
                txtNivelDescricao.text = "Seu desempenho mostra uma preparação avançada."
                progressNivel.max = 1000
                progressNivel.progress = totalQuestoes
                txtProximoNivel.text = "$totalQuestoes / 1000 questões para o próximo nível"
            }

            else -> {
                txtNivel.text = "💎 Mestre Stuble"
                txtNivelDescricao.text = "Você alcançou o nível máximo da plataforma."
                progressNivel.max = totalQuestoes
                progressNivel.progress = totalQuestoes
                txtProximoNivel.text = "Nível máximo alcançado"
            }
        }
    }

    private fun atualizarConquistas(
        view: View,
        totalQuestoes: Int,
        taxaAcerto: Int
    ) {
        val progressQuestoes =
            view.findViewById<ProgressBar>(R.id.progressConquistaQuestoes)

        val txtQuestoesInfo =
            view.findViewById<TextView>(R.id.txtConquistaQuestoesInfo)

        val progressPrecisao =
            view.findViewById<ProgressBar>(R.id.progressConquistaPrecisao)

        val txtPrecisaoInfo =
            view.findViewById<TextView>(R.id.txtConquistaPrecisaoInfo)

        progressQuestoes.max = 100
        progressQuestoes.progress = totalQuestoes.coerceAtMost(100)
        txtQuestoesInfo.text =
            "${totalQuestoes.coerceAtMost(100)} / 100 questões respondidas"

        progressPrecisao.max = 80
        progressPrecisao.progress = taxaAcerto.coerceAtMost(80)
        txtPrecisaoInfo.text =
            "$taxaAcerto% / 80% de acerto"
    }
}