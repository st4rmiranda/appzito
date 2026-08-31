package com.company.stuble.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.company.stuble.model.Pergunta
import com.google.firebase.auth.FirebaseAuth
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PREFS_NAME = "revisao_erros_usuario_local"
private const val KEY_ERROS = "questoes_erradas"

@RunWith(RobolectricTestRunner::class)
class RevisaoErrosManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        mockkStatic(FirebaseAuth::class)
        val auth = mockk<FirebaseAuth>(relaxed = true)
        every { auth.currentUser } returns null
        every { FirebaseAuth.getInstance() } returns auth
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseAuth::class)
    }

    private fun pergunta(
        texto: String = "Pergunta errada de teste?",
        area: String = "Matemática"
    ) = Pergunta(
        pergunta = texto,
        opcoes = listOf("A", "B", "C", "D"),
        correta = 0,
        explicacao = "Explicação de teste.",
        area = area
    )

    @Test
    fun `erro registrado pode ser recuperado`() {
        val original = pergunta()

        RevisaoErrosManager.registrarErro(context, original)

        val erros = RevisaoErrosManager.obterErros(context)
        assertEquals(1, erros.size)
        assertEquals(original.pergunta, erros.first().pergunta)
    }

    @Test
    fun `erro duplicado normalizado e ignorado`() {
        RevisaoErrosManager.registrarErro(context, pergunta(texto = "Qual e a capital do Brasil?"))
        RevisaoErrosManager.registrarErro(context, pergunta(texto = "  qual E a capital do brasil?  "))

        assertEquals(1, RevisaoErrosManager.quantidade(context))
    }

    @Test
    fun `pergunta invalida nao e registrada`() {
        val invalida = pergunta().copy(opcoes = listOf("A", "B"))

        RevisaoErrosManager.registrarErro(context, invalida)

        assertEquals(0, RevisaoErrosManager.quantidade(context))
    }

    @Test
    fun `limite de 20 descarta o erro mais antigo`() {
        repeat(21) { indice ->
            RevisaoErrosManager.registrarErro(context, pergunta(texto = "Pergunta número $indice?"))
        }

        val erros = RevisaoErrosManager.obterErros(context)

        assertEquals(20, erros.size)
        assertTrue(erros.none { it.pergunta == "Pergunta número 0?" })
        assertTrue(erros.any { it.pergunta == "Pergunta número 20?" })
    }

    @Test
    fun `removerErro tira a pergunta certa da lista`() {
        val alvo = pergunta(texto = "Pergunta a remover?")
        RevisaoErrosManager.registrarErro(context, alvo)
        RevisaoErrosManager.registrarErro(context, pergunta(texto = "Outra pergunta?"))

        RevisaoErrosManager.removerErro(context, alvo)

        val restantes = RevisaoErrosManager.obterErros(context)
        assertEquals(1, restantes.size)
        assertEquals("Outra pergunta?", restantes.first().pergunta)
    }

    @Test
    fun `removerErro e no-op quando a pergunta nao existe`() {
        RevisaoErrosManager.registrarErro(context, pergunta())

        RevisaoErrosManager.removerErro(context, pergunta(texto = "Pergunta que nunca foi salva?"))

        assertEquals(1, RevisaoErrosManager.quantidade(context))
    }

    @Test
    fun `cache corrompido se recupera sozinho`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ERROS, "{ isto nao e um json valido")
            .apply()

        val erros = RevisaoErrosManager.obterErros(context)

        assertTrue(erros.isEmpty())
    }

    @Test
    fun `lista vazia tem quantidade zero`() {
        assertEquals(0, RevisaoErrosManager.quantidade(context))
    }
}
