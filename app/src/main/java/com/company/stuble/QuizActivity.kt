package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.company.stuble.data.DificuldadeAdaptativaManager
import com.company.stuble.data.GamificacaoManager
import com.company.stuble.data.QuizRepository
import com.company.stuble.data.QuizStateManager
import com.company.stuble.data.ResultadoGamificacao
import com.company.stuble.data.PerfilManager
import com.company.stuble.data.ProgressManager
import com.company.stuble.data.RevisaoErrosManager
import com.company.stuble.model.Pergunta
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.concurrent.Executors

class QuizActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "QuizActivity"
        private const val TOTAL_QUESTOES = 20

    }

    private lateinit var txtCount: TextView
    private lateinit var txtQuestion: TextView
    private lateinit var txtExplanation: TextView
    private lateinit var quizProgressBar: ProgressBar
    private lateinit var rgOptions: RadioGroup
    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnVoltar: MaterialButton
    private lateinit var cardExplanation: MaterialCardView
    private lateinit var quizScrollView: NestedScrollView

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var quizRepository: QuizRepository
    private var carregandoPreload = false

    private var perguntaAtual: Pergunta? = null
    private var respondidas = 0
    private var modoExplicacaoAtivo = false
    private var processandoAvanco = false
    private var respostaAtualContabilizada = false

    private var filtroCompetencia: String? = null
    private var ehTreinoLivre = false

    @Volatile
    private var ultimaMensagemErro = "Não foi possível gerar a questão."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        vincularViews()

        filtroCompetencia = intent.getStringExtra("COMPETENCIA_FILTRO")
        ehTreinoLivre = intent.getBooleanExtra("EH_TREINO_LIVRE", false)
        quizRepository = QuizRepository(applicationContext)

        /*
         * Recupera o progresso salvo do dia. Se o aluno respondeu 7 questões,
         * a tela será aberta exibindo a questão 08 de 20.
         */
        respondidas = if (ehTreinoLivre) {
            0
        } else {
            ProgressManager
                .getQuestoesRespondidasHoje(this)
                .coerceIn(0, TOTAL_QUESTOES)
        }

        configurarInterface()
        configurarCliqueBotao()
        configurarBotaoVoltar()

        if (respondidas >= TOTAL_QUESTOES && !ehTreinoLivre) {
            abrirTelaDeConclusao()
            return
        }

        carregarPrimeiraPergunta()
    }

    private fun vincularViews() {
        txtCount = findViewById(R.id.txtCount)
        txtQuestion = findViewById(R.id.txtQuestion)
        txtExplanation = findViewById(R.id.txtExplanation)
        quizProgressBar = findViewById(R.id.quizProgressBar)
        rgOptions = findViewById(R.id.rgOptions)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnVoltar = findViewById(R.id.btnVoltar)
        cardExplanation = findViewById(R.id.cardExplanation)
        quizScrollView = findViewById(R.id.quizScrollView)
    }

    private fun configurarInterface() {
        quizProgressBar.max = TOTAL_QUESTOES
        quizProgressBar.progress = respondidas
        cardExplanation.isVisible = false
        atualizarContador()
    }

    private fun configurarCliqueBotao() {
        btnConfirm.setOnClickListener {
            if (modoExplicacaoAtivo) {
                proximaQuestaoOuFinalizar()
            } else {
                verificarResposta()
            }
        }
    }

    private fun configurarBotaoVoltar() {
        btnVoltar.setOnClickListener {
            val intentInicio = Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            startActivity(intentInicio)
            finish()
        }
    }

    private fun atualizarContador() {
        val numeroExibido =
            (respondidas + 1).coerceIn(1, TOTAL_QUESTOES)

        txtCount.text = "Questão %02d de %02d".format(
            numeroExibido,
            TOTAL_QUESTOES
        )
    }

    private fun carregarPrimeiraPergunta() {
        val restaurada = QuizStateManager.restaurar(
            context = this,
            filtro = filtroCompetencia,
            treinoLivre = ehTreinoLivre
        )

        if (restaurada != null) {
            perguntaAtual = restaurada
            exibirPerguntaNoLayout(restaurada)
            manterBuffer()
            return
        }

        carregarPerguntaDoRepositorio(
            mensagem = "Preparando sua questão…"
        )
    }

    private fun manterBuffer() {
        if (carregandoPreload) return
        carregandoPreload = true

        executor.execute {
            try {
                val perfil = PerfilManager.carregarPerfil(this)
                val dificuldade =
                    DificuldadeAdaptativaManager.obterDificuldadeAtual(
                        this,
                        perfil.dificuldadePreferida
                    )

                quizRepository.precarregarUmaPergunta(
                    filtroArea = filtroCompetencia,
                    dificuldade = dificuldade
                )
            } finally {
                carregandoPreload = false
            }
        }
    }

    private fun carregarPerguntaUrgente() {
        carregarPerguntaDoRepositorio(
            mensagem = "Carregando a próxima questão…"
        )
    }

    private fun carregarPerguntaDoRepositorio(
        mensagem: String
    ) {
        mostrarCarregamento(mensagem)

        executor.execute {
            val perfil = PerfilManager.carregarPerfil(this)
            val dificuldade =
                DificuldadeAdaptativaManager.obterDificuldadeAtual(
                    this,
                    perfil.dificuldadePreferida
                )

            val resultado = quizRepository.obterProximaPergunta(
                filtroArea = filtroCompetencia,
                dificuldade = dificuldade
            )

            runOnUiThread {
                if (!activityDisponivel()) return@runOnUiThread

                processandoAvanco = false

                val pergunta = resultado.pergunta
                if (pergunta == null) {
                    ultimaMensagemErro =
                        resultado.mensagemErro
                            ?: "Não foi possível carregar a questão."
                    mostrarErroComTentativa()
                    return@runOnUiThread
                }

                perguntaAtual = pergunta
                exibirPerguntaNoLayout(pergunta)

                if (resultado.origem == "cache_fallback") {
                    Toast.makeText(
                        this,
                        "Você está usando uma questão salva no aparelho.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                manterBuffer()
            }
        }
    }

    private fun mostrarCarregamento(mensagem: String) {
        txtQuestion.text = mensagem
        rgOptions.clearCheck()

        // Não removemos as alternativas do XML. Apenas escondemos e desabilitamos
        // temporariamente enquanto a próxima questão é carregada.
        for (i in 0 until rgOptions.childCount) {
            val opcao = rgOptions.getChildAt(i)
            opcao.visibility = View.GONE
            opcao.isEnabled = false
        }

        cardExplanation.isVisible = false
        btnConfirm.isEnabled = false
        btnConfirm.text = "CARREGANDO…"
    }

    private fun mostrarErroComTentativa() {
        txtQuestion.text = ultimaMensagemErro
        cardExplanation.isVisible = false
        btnConfirm.isEnabled = true
        btnConfirm.text = "TENTAR NOVAMENTE"
        modoExplicacaoAtivo = false
        processandoAvanco = false

        /*
         * Não existe repetição automática. Uma nova chamada só ocorre quando
         * o próprio usuário toca em “Tentar novamente”.
         */
        btnConfirm.setOnClickListener {
            carregarPerguntaUrgente()
        }
    }

    private fun exibirPerguntaNoLayout(pergunta: Pergunta) {
        modoExplicacaoAtivo = false
        processandoAvanco = false
        respostaAtualContabilizada = false

        txtQuestion.text = pergunta.pergunta
        txtExplanation.text = ""

        QuizStateManager.salvar(
            context = this,
            pergunta = pergunta,
            filtro = filtroCompetencia,
            treinoLivre = ehTreinoLivre
        )
        cardExplanation.isVisible = false

        rgOptions.clearCheck()

        val opcoes = listOfNotNull(
            findViewById<RadioButton?>(R.id.opt1),
            findViewById<RadioButton?>(R.id.opt2),
            findViewById<RadioButton?>(R.id.opt3),
            findViewById<RadioButton?>(R.id.opt4)
        )

        if (opcoes.size != 4) {
            ultimaMensagemErro =
                "As alternativas não foram encontradas no layout activity_quiz.xml."
            Log.e(TAG, ultimaMensagemErro)
            mostrarErroComTentativa()
            return
        }

        opcoes.forEachIndexed { indice, radioButton ->
            radioButton.text = pergunta.opcoes.getOrNull(indice).orEmpty()
            radioButton.tag = indice
            radioButton.visibility = View.VISIBLE
            radioButton.isEnabled = true
            radioButton.isChecked = false
        }

        btnConfirm.setOnClickListener {
            if (modoExplicacaoAtivo) {
                proximaQuestaoOuFinalizar()
            } else {
                verificarResposta()
            }
        }

        btnConfirm.text = "CONFIRMAR RESPOSTA"
        btnConfirm.isEnabled = true

        atualizarContador()

        quizScrollView.post {
            quizScrollView.smoothScrollTo(0, 0)
        }
    }

    private fun verificarResposta() {
        if (processandoAvanco || respostaAtualContabilizada) return

        val idSelecionado = rgOptions.checkedRadioButtonId

        if (idSelecionado == -1) {
            Toast.makeText(
                this,
                "Selecione uma alternativa.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val radioSelecionado =
            findViewById<RadioButton>(idSelecionado)
        val indiceSelecionado = radioSelecionado.tag as? Int

        val pergunta = perguntaAtual ?: return
        if (indiceSelecionado == null) return

        val acertou = indiceSelecionado == pergunta.correta
        val perfil = PerfilManager.carregarPerfil(this)

        DificuldadeAdaptativaManager.registrarResposta(
            context = this,
            acertou = acertou,
            dificuldadePreferida = perfil.dificuldadePreferida
        )

        /*
         * A questão é contabilizada no momento em que a resposta é confirmada.
         * Assim, mesmo que o aluno saia durante a explicação, o progresso não
         * volta para trás ao abrir o Quiz novamente.
         */
        contabilizarQuestaoRespondida()

        val resultadoGamificacao = GamificacaoManager.registrarResposta(
            context = this,
            acertou = acertou,
            area = pergunta.area,
            respondidasHojeDepois = respondidas,
            ehMissaoDiaria = !ehTreinoLivre
        )

        respostaAtualContabilizada = true
        QuizStateManager.limpar(this)
        exibirRecompensasGamificacao(resultadoGamificacao)

        desabilitarAlternativas()

        if (acertou) {
            Toast.makeText(
                this,
                "Resposta correta! ✅",
                Toast.LENGTH_SHORT
            ).show()

            proximaQuestaoOuFinalizar()
        } else {
            RevisaoErrosManager.registrarErro(this, pergunta)

            modoExplicacaoAtivo = true
            txtExplanation.text = pergunta.explicacao
            cardExplanation.isVisible = true
            btnConfirm.text = "CONTINUAR"

            quizScrollView.post {
                quizScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun contabilizarQuestaoRespondida() {
        if (respostaAtualContabilizada) return

        if (ehTreinoLivre) {
            respondidas = (respondidas + 1).coerceAtMost(TOTAL_QUESTOES)
        } else {
            respondidas = ProgressManager
                .adicionarQuestaoRespondida(this)
                .coerceIn(0, TOTAL_QUESTOES)
        }

        quizProgressBar.progress = respondidas
    }

    private fun exibirRecompensasGamificacao(
        resultado: ResultadoGamificacao
    ) {
        animarXpGanho(resultado.xpGanho)

        if (resultado.subiuNivel) {
            android.app.AlertDialog.Builder(this)
                .setTitle("LEVEL UP! ${resultado.nivelAtual.emoji}")
                .setMessage(
                    "Você chegou ao nível ${resultado.nivelAtual.numero}: " +
                            "${resultado.nivelAtual.titulo}!\n\n" +
                            "Continue assim para conquistar o próximo título."
                )
                .setPositiveButton("BORA!") { dialog, _ ->
                    dialog.dismiss()
                    mostrarConquistasNovas(resultado)
                }
                .show()
        } else {
            mostrarConquistasNovas(resultado)
        }

        if (resultado.desafioConcluidoAgora) {
            Toast.makeText(
                this,
                "Desafio diário concluído! +40 XP ⚡",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun mostrarConquistasNovas(
        resultado: ResultadoGamificacao
    ) {
        val conquista = resultado.novasConquistas.firstOrNull() ?: return

        android.app.AlertDialog.Builder(this)
            .setTitle("${conquista.emoji} Conquista desbloqueada!")
            .setMessage(
                "${conquista.titulo}\n\n${conquista.descricao}\n\n" +
                        "+${conquista.recompensaXp} XP"
            )
            .setPositiveButton("INCRÍVEL", null)
            .show()
    }

    private fun animarXpGanho(xp: Int) {
        val raiz = window.decorView
            .findViewById<android.widget.FrameLayout>(android.R.id.content)

        val textoXp = TextView(this).apply {
            text = "+$xp XP"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#4F46E5"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            alpha = 0f
            translationY = 70f
        }

        val parametros = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP or android.view.Gravity.END
        ).apply {
            topMargin = 110
            marginEnd = 28
        }

        raiz.addView(textoXp, parametros)

        textoXp.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .withEndAction {
                textoXp.animate()
                    .alpha(0f)
                    .translationY(-70f)
                    .setStartDelay(750)
                    .setDuration(450)
                    .withEndAction {
                        raiz.removeView(textoXp)
                    }
                    .start()
            }
            .start()
    }

    private fun desabilitarAlternativas() {
        for (i in 0 until rgOptions.childCount) {
            rgOptions.getChildAt(i).isEnabled = false
        }
    }

    private fun proximaQuestaoOuFinalizar() {
        if (processandoAvanco) return

        /*
         * Proteção adicional caso este método seja chamado por algum fluxo
         * futuro sem que a questão atual tenha sido contabilizada antes.
         */
        if (!respostaAtualContabilizada) {
            contabilizarQuestaoRespondida()
            respostaAtualContabilizada = true
        }

        processandoAvanco = true
        btnConfirm.isEnabled = false

        if (respondidas >= TOTAL_QUESTOES) {
            abrirTelaDeConclusao()
            return
        }

        modoExplicacaoAtivo = false
        cardExplanation.isVisible = false
        btnConfirm.text = "CARREGANDO…"
        atualizarContador()

        carregarPerguntaUrgente()
    }

    private fun abrirTelaDeConclusao() {
        if (!activityDisponivel()) return

        val intentConclusao =
            Intent(this, MissionCompleteActivity::class.java).apply {
                putExtra("EH_TREINO_LIVRE", ehTreinoLivre)
            }

        startActivity(intentConclusao)
        finish()
    }

    private fun activityDisponivel(): Boolean {
        return !isFinishing &&
                !isDestroyed &&
                !executor.isShutdown
    }

    override fun onDestroy() {
        executor.shutdownNow()
        if (::quizRepository.isInitialized) {
            quizRepository.fechar()
        }
        super.onDestroy()
    }
}