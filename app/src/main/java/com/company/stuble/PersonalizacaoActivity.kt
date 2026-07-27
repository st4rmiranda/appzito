package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.company.stuble.data.OpcoesPerfil
import com.company.stuble.data.PerfilManager
import com.company.stuble.model.PerfilEstudante
import com.google.android.material.button.MaterialButton

class PersonalizacaoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODO_EDICAO = "MODO_EDICAO"
    }

    private enum class Etapa {
        ANO,
        AREA,
        CURSO,
        OBJETIVO,
        DIFICULDADE,
        MATERIAS
    }

    private lateinit var progressEtapas: ProgressBar
    private lateinit var txtEtapa: TextView
    private lateinit var txtTituloPersonalizacao: TextView
    private lateinit var txtPergunta: TextView
    private lateinit var txtDescricao: TextView
    private lateinit var containerOpcoes: LinearLayout
    private lateinit var btnVoltar: MaterialButton
    private lateinit var btnContinuar: MaterialButton

    private val etapas = Etapa.values()
    private var indiceEtapa = 0
    private var modoEdicao = false
    private var atualizandoCheckboxes = false

    private var anoSelecionado: String? = null
    private var areaSelecionada: String? = null
    private var cursoSelecionado: String? = null
    private var objetivoSelecionado: String? = null
    private var dificuldadeSelecionada: String? = null
    private val materiasSelecionadas = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personalizacao)

        modoEdicao = intent.getBooleanExtra(EXTRA_MODO_EDICAO, false)

        vincularViews()
        carregarPerfilSalvo()
        configurarCliques()
        renderizarEtapa()
    }

    private fun vincularViews() {
        progressEtapas = findViewById(R.id.progressEtapas)
        txtEtapa = findViewById(R.id.txtEtapa)
        txtTituloPersonalizacao = findViewById(R.id.txtTituloPersonalizacao)
        txtPergunta = findViewById(R.id.txtPergunta)
        txtDescricao = findViewById(R.id.txtDescricao)
        containerOpcoes = findViewById(R.id.containerOpcoes)
        btnVoltar = findViewById(R.id.btnVoltar)
        btnContinuar = findViewById(R.id.btnContinuar)
    }

    private fun carregarPerfilSalvo() {
        if (!PerfilManager.onboardingConcluido(this)) return

        val perfil = PerfilManager.carregarPerfil(this)

        anoSelecionado = perfil.anoEscolar.takeIf { it.isNotBlank() }
        areaSelecionada = perfil.areaInteresse.takeIf { it.isNotBlank() }
        cursoSelecionado = perfil.cursoDesejado.takeIf { it.isNotBlank() }
        objetivoSelecionado = perfil.objetivo.takeIf { it.isNotBlank() }
        dificuldadeSelecionada =
            perfil.dificuldadePreferida.takeIf { it.isNotBlank() }
        materiasSelecionadas.addAll(perfil.materiasDificuldade)
    }

    private fun configurarCliques() {
        btnVoltar.setOnClickListener {
            if (indiceEtapa > 0) {
                indiceEtapa--
                renderizarEtapa()
            } else if (modoEdicao) {
                finish()
            }
        }

        btnContinuar.setOnClickListener {
            continuar()
        }
    }

    private fun etapaAtual(): Etapa = etapas[indiceEtapa]

    private fun renderizarEtapa() {
        val total = etapas.size
        val etapa = etapaAtual()

        progressEtapas.max = total
        progressEtapas.progress = indiceEtapa + 1
        txtEtapa.text = "ETAPA ${indiceEtapa + 1} DE $total"

        txtTituloPersonalizacao.text =
            if (modoEdicao) "Atualize seu perfil" else "Personalize seus estudos"

        btnVoltar.visibility =
            if (indiceEtapa == 0 && !modoEdicao) View.INVISIBLE else View.VISIBLE

        btnContinuar.text =
            if (indiceEtapa == etapas.lastIndex) "FINALIZAR" else "CONTINUAR"

        containerOpcoes.removeAllViews()

        when (etapa) {
            Etapa.ANO -> {
                txtPergunta.text = "Em qual ano da escola você está?"
                txtDescricao.text =
                    "Isso ajuda a ajustar a linguagem e a profundidade das questões."
                adicionarOpcoesUnicas(
                    OpcoesPerfil.anosEscolares,
                    anoSelecionado
                )
            }

            Etapa.AREA -> {
                txtPergunta.text = "Qual área mais combina com você?"
                txtDescricao.text =
                    "Usaremos essa informação apenas para personalizar exemplos e contextos."
                adicionarOpcoesUnicas(
                    OpcoesPerfil.cursosPorArea.keys.toList(),
                    areaSelecionada
                )
            }

            Etapa.CURSO -> {
                txtPergunta.text = "Qual curso você pretende fazer?"
                txtDescricao.text =
                    "Você poderá alterar essa escolha depois no seu perfil."

                val area = areaSelecionada
                val cursos = if (area == null) {
                    emptyList()
                } else {
                    OpcoesPerfil.cursosPorArea[area].orEmpty()
                }

                adicionarOpcoesUnicas(cursos, cursoSelecionado)
            }

            Etapa.OBJETIVO -> {
                txtPergunta.text = "Qual é seu principal objetivo?"
                txtDescricao.text =
                    "O estilo das questões será aproximado do tipo de prova escolhido."
                adicionarOpcoesUnicas(
                    OpcoesPerfil.objetivos,
                    objetivoSelecionado
                )
            }

            Etapa.DIFICULDADE -> {
                txtPergunta.text = "Qual dificuldade você prefere?"
                txtDescricao.text =
                    "No modo adaptativo, o nível muda conforme seus acertos e erros."
                adicionarOpcoesUnicas(
                    OpcoesPerfil.dificuldades,
                    dificuldadeSelecionada
                )
            }

            Etapa.MATERIAS -> {
                txtPergunta.text = "Em quais matérias você sente mais dificuldade?"
                txtDescricao.text =
                    "Marque uma ou mais opções. Isso dará prioridade a essas matérias."
                adicionarOpcoesMultiplas(
                    OpcoesPerfil.materias,
                    materiasSelecionadas
                )
            }
        }
    }

    private fun adicionarOpcoesUnicas(
        opcoes: List<String>,
        opcaoMarcada: String?
    ) {
        opcoes.forEach { opcao ->
            val radioButton = RadioButton(this).apply {
                id = View.generateViewId()
                text = opcao
                textSize = 16f
                minHeight = dp(52)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isChecked = opcao == opcaoMarcada

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }

                setOnClickListener {
                    marcarSomenteEsteRadio(this)
                    salvarRespostaUnica(opcao)
                }
            }

            containerOpcoes.addView(radioButton)
        }
    }

    private fun marcarSomenteEsteRadio(selecionado: RadioButton) {
        for (i in 0 until containerOpcoes.childCount) {
            val filho = containerOpcoes.getChildAt(i)
            if (filho is RadioButton) {
                filho.isChecked = filho === selecionado
            }
        }
    }

    private fun salvarRespostaUnica(opcao: String) {
        when (etapaAtual()) {
            Etapa.ANO -> anoSelecionado = opcao

            Etapa.AREA -> {
                if (areaSelecionada != opcao) {
                    cursoSelecionado = null
                }
                areaSelecionada = opcao
            }

            Etapa.CURSO -> cursoSelecionado = opcao
            Etapa.OBJETIVO -> objetivoSelecionado = opcao
            Etapa.DIFICULDADE -> dificuldadeSelecionada = opcao
            Etapa.MATERIAS -> Unit
        }
    }

    private fun adicionarOpcoesMultiplas(
        opcoes: List<String>,
        marcadas: Set<String>
    ) {
        opcoes.forEach { opcao ->
            val checkBox = CheckBox(this).apply {
                id = View.generateViewId()
                text = opcao
                textSize = 16f
                minHeight = dp(52)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isChecked = opcao in marcadas

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }

                setOnCheckedChangeListener { _, marcado ->
                    if (atualizandoCheckboxes) return@setOnCheckedChangeListener
                    tratarSelecaoMateria(opcao, marcado)
                }
            }

            containerOpcoes.addView(checkBox)
        }
    }

    private fun tratarSelecaoMateria(opcao: String, marcado: Boolean) {
        if (marcado) {
            if (opcao == OpcoesPerfil.NENHUMA_MATERIA) {
                materiasSelecionadas.clear()
                materiasSelecionadas.add(opcao)
                desmarcarOutrasMaterias(opcao)
            } else {
                materiasSelecionadas.remove(OpcoesPerfil.NENHUMA_MATERIA)
                materiasSelecionadas.add(opcao)
                desmarcarOpcaoNenhuma()
            }
        } else {
            materiasSelecionadas.remove(opcao)
        }
    }

    private fun desmarcarOutrasMaterias(excecao: String) {
        atualizandoCheckboxes = true
        try {
            for (i in 0 until containerOpcoes.childCount) {
                val filho = containerOpcoes.getChildAt(i)
                if (filho is CheckBox && filho.text.toString() != excecao) {
                    filho.isChecked = false
                }
            }
        } finally {
            atualizandoCheckboxes = false
        }
    }

    private fun desmarcarOpcaoNenhuma() {
        atualizandoCheckboxes = true
        try {
            for (i in 0 until containerOpcoes.childCount) {
                val filho = containerOpcoes.getChildAt(i)
                if (
                    filho is CheckBox &&
                    filho.text.toString() == OpcoesPerfil.NENHUMA_MATERIA
                ) {
                    filho.isChecked = false
                    break
                }
            }
        } finally {
            atualizandoCheckboxes = false
        }
    }

    private fun continuar() {
        if (!respostaAtualValida()) {
            Toast.makeText(
                this,
                mensagemValidacao(),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (indiceEtapa < etapas.lastIndex) {
            indiceEtapa++
            renderizarEtapa()
        } else {
            finalizarPersonalizacao()
        }
    }

    private fun respostaAtualValida(): Boolean {
        return when (etapaAtual()) {
            Etapa.ANO -> !anoSelecionado.isNullOrBlank()
            Etapa.AREA -> !areaSelecionada.isNullOrBlank()
            Etapa.CURSO -> !cursoSelecionado.isNullOrBlank()
            Etapa.OBJETIVO -> !objetivoSelecionado.isNullOrBlank()
            Etapa.DIFICULDADE -> !dificuldadeSelecionada.isNullOrBlank()
            Etapa.MATERIAS -> materiasSelecionadas.isNotEmpty()
        }
    }

    private fun mensagemValidacao(): String {
        return if (etapaAtual() == Etapa.MATERIAS) {
            "Marque uma opção ou selecione “Nenhuma matéria em especial”."
        } else {
            "Selecione uma opção para continuar."
        }
    }

    private fun finalizarPersonalizacao() {
        val materiasFinais = materiasSelecionadas
            .filterNot { it == OpcoesPerfil.NENHUMA_MATERIA }
            .toSet()

        val perfil = PerfilEstudante(
            anoEscolar = requireNotNull(anoSelecionado),
            areaInteresse = requireNotNull(areaSelecionada),
            cursoDesejado = requireNotNull(cursoSelecionado),
            objetivo = requireNotNull(objetivoSelecionado),
            dificuldadePreferida = requireNotNull(dificuldadeSelecionada),
            materiasDificuldade = materiasFinais
        )

        PerfilManager.salvarPerfil(this, perfil)

        Toast.makeText(
            this,
            if (modoEdicao) "Perfil atualizado!" else "Perfil criado com sucesso!",
            Toast.LENGTH_SHORT
        ).show()

        if (modoEdicao) {
            setResult(RESULT_OK)
            finish()
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun dp(valor: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
