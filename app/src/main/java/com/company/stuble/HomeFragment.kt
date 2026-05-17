package com.company.stuble

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar // IMPORT ADICIONADO AQUI

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class HomeFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configuração do clique do botão para abrir o Quiz
        val btnIniciar = view.findViewById<Button>(R.id.btnStartQuestions)
        btnIniciar.setOnClickListener {
            val intent = Intent(context, QuizActivity::class.java)
            startActivity(intent)
        }

        // 2. O LUGAR CORRETO DA ANIMAÇÃO É AQUI!
        // Como a view já foi criada, conseguimos achar a ProgressBar com segurança
        val progressBar = view.findViewById<ProgressBar>(R.id.homeProgressBar)

        // Executa a animação babadeira assim que o estudante entra na tela
        android.animation.ObjectAnimator.ofInt(progressBar, "progress", 0, 35)
            .setDuration(1000) // 1 segundo de animação correndo
            .start()
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}