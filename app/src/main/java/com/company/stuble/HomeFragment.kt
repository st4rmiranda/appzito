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
        return inflater.inflate(
            R.layout.fragment_home,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        val txtNome = view.findViewById<TextView>(R.id.txtNomeUsuario)

        val user = FirebaseAuth.getInstance().currentUser

        val nomeUsuario = user?.displayName ?: "Estudante"

        txtNome.text = "Olá, $nomeUsuario 🚀"

        super.onViewCreated(view, savedInstanceState)

        val btnIniciar =
            view.findViewById<Button>(R.id.btnStartQuestions)

        btnIniciar.setOnClickListener {

            val intent =
                Intent(requireContext(), QuizActivity::class.java)

            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        atualizarProgresso()
    }

    private fun atualizarProgresso() {

        val respondidas =
            ProgressManager.getQuestoesRespondidas(
                requireContext()
            )

        val percentual =
            ProgressManager.getPercentual(
                requireContext()
            )

        val faltam =
            maxOf(0, 20 - respondidas)

        val progressBar =
            requireView().findViewById<ProgressBar>(
                R.id.homeProgressBar
            )

        val txtPercentual =
            requireView().findViewById<TextView>(
                R.id.txtPercentual
            )

        val txtMeta =
            requireView().findViewById<TextView>(
                R.id.txtMetaDiaria
            )

        ObjectAnimator.ofInt(
            progressBar,
            "progress",
            progressBar.progress,
            percentual
        ).apply {
            duration = 700
            start()
        }

        txtPercentual.text = "$percentual%"

        txtMeta.text =
            "Faltam $faltam questões para a meta de hoje!"
    }
}