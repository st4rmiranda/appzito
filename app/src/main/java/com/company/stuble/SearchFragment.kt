package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class SearchFragment : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    // 1. O onCreateView apenas infla e retorna a View do layout
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    // 2. O onViewCreated é o lugar correto para programar a lógica dos cliques!
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etPesquisa = view.findViewById<EditText>(R.id.etPesquisa)
        val btnPesquisar = view.findViewById<ImageView>(R.id.btnPesquisar)

        // LÓGICA DA PESQUISA (Explicação da IA)
        btnPesquisar.setOnClickListener {
            val materia = etPesquisa.text.toString().trim()
            if (materia.isNotEmpty()) {
                val intent = Intent(context, ExplanationActivity::class.java).apply {
                    putExtra("MATERIA_PESQUISADA", materia)
                }
                startActivity(intent)
            } else {
                Toast.makeText(context, "Digite uma matéria para pesquisar!", Toast.LENGTH_SHORT).show()
            }
        }

        // LÓGICA DOS CARDS (Quiz focado por competência e sem contar na meta)
        val cliqueCardQuiz = View.OnClickListener { v ->
            val competencaEscolhida = when(v.id) {
                R.id.cardLinguagens -> "Linguagens, Códigos e suas Tecnologias"
                R.id.cardExatas -> "Matemática e suas Tecnologias"
                R.id.cardBiologia -> "Ciências da Natureza e suas Tecnologias"
                R.id.cardHumanas -> "Ciências Humanas e suas Tecnologias"
                else -> ""
            }

            val intent = Intent(context, QuizActivity::class.java).apply {
                putExtra("COMPETENCIA_FILTRO", competencaEscolhida)
                putExtra("EH_TREINO_LIVRE", true) // Tag para não contar na sequência diária
            }
            startActivity(intent)
        }

        // Configura o evento de clique em cada CardView da tela
        view.findViewById<CardView>(R.id.cardLinguagens).setOnClickListener(cliqueCardQuiz)
        view.findViewById<CardView>(R.id.cardExatas).setOnClickListener(cliqueCardQuiz)
        view.findViewById<CardView>(R.id.cardBiologia).setOnClickListener(cliqueCardQuiz)
        view.findViewById<CardView>(R.id.cardHumanas).setOnClickListener(cliqueCardQuiz)
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            SearchFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}