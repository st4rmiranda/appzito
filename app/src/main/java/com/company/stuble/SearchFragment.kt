package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etPesquisa = view.findViewById<EditText>(R.id.etPesquisa)
        val btnPesquisar = view.findViewById<ImageView>(R.id.btnPesquisar)

        // LÓGICA DA PESQUISA COM ESCOLA DE FORMATO (Texto ou Mapa Mental)
        btnPesquisar.setOnClickListener {
            val materia = etPesquisa.text.toString().trim()
            if (materia.isNotEmpty()) {
                mostrarDialogoEscolha(materia)
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
                putExtra("EH_TREINO_LIVRE", true)
            }
            startActivity(intent)
        }

        // Configura o evento de clique em cada CardView da tela
        view.findViewById<View>(R.id.cardLinguagens).setOnClickListener(cliqueCardQuiz)
        view.findViewById<View>(R.id.cardExatas).setOnClickListener(cliqueCardQuiz)
        view.findViewById<View>(R.id.cardBiologia).setOnClickListener(cliqueCardQuiz)
        view.findViewById<View>(R.id.cardHumanas).setOnClickListener(cliqueCardQuiz)

        // Botão Recomendado
        view.findViewById<MaterialButton>(R.id.btnComecarRecomendado).setOnClickListener {
            val intent = Intent(context, QuizActivity::class.java).apply {
                putExtra("COMPETENCIA_FILTRO", "Matemática e suas Tecnologias")
                putExtra("EH_TREINO_LIVRE", true)
            }
            startActivity(intent)
        }

        configurarBuscasPopulares(view)
    }

    private fun configurarBuscasPopulares(view: View) {
        val temasDisponiveis = listOf(
            "Bhaskara", "Mitose", "Crase", "Revolução Francesa",
            "Estequiometria", "Modernismo", "Guerra Fria", "Leis de Newton",
            "Tabela Periódica", "Globalização", "Geometria Espacial", "Sintaxe"
        )

        // Seleciona 4 temas aleatórios baseados no dia do mês para manter consistência no mesmo dia
        val diaDoMes = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        val random = java.util.Random(diaDoMes.toLong())
        val temasHoje = temasDisponiveis.shuffled(random).take(4)

        val chipsIds = listOf(R.id.chipBhaskara, R.id.chipMitose, R.id.chipCrase, R.id.chipRevolucao)

        chipsIds.forEachIndexed { index, id ->
            val button = view.findViewById<MaterialButton>(id)
            val tema = temasHoje.getOrNull(index) ?: temasDisponiveis[index]

            button.text = tema
            button.setOnClickListener {
                mostrarDialogoEscolha(tema)
            }
        }
    }

    private fun mostrarDialogoEscolha(materia: String) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_format_choice, null)

        view.findViewById<TextView>(R.id.txtDialogTitle).text = materia

        view.findViewById<View>(R.id.cardOptionTexto).setOnClickListener {
            abrirExplonacao(materia, "TEXTO")
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.cardOptionMapa).setOnClickListener {
            abrirExplonacao(materia, "MAPA_MENTAL")
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // Função auxiliar para disparar a Intent com as duas chaves necessárias
    private fun abrirExplonacao(materia: String, tipo: String) {
        val intent = Intent(context, ExplanationActivity::class.java).apply {
            putExtra("MATERIA_PESQUISADA", materia)
            putExtra("TIPO_CONTEUDO", tipo)
        }
        startActivity(intent)
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