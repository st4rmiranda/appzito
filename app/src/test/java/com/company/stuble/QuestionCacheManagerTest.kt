package com.company.stuble

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Espelha a convenção de nome de arquivo descrita no CLAUDE.md (PREFIXO_PREFS + uid),
 * já que QuestionCacheManager não expõe essa String publicamente.
 */
private const val PREFS_NAME = "question_cache_usuario_local"
private const val KEY_QUESTIONS = "cached_questions"
private const val KEY_USED_DATE = "used_date"

@RunWith(RobolectricTestRunner::class)
class QuestionCacheManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // FirebaseAuth.getInstance() não é mockado, viraria uma chamada de rede/SDK real.
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
        texto: String = "Pergunta de teste?",
        area: String = "Matemática"
    ) = Pergunta(
        pergunta = texto,
        opcoes = listOf("A", "B", "C", "D"),
        correta = 0,
        explicacao = "Explicação de teste.",
        area = area
    )

    @Test
    fun `pergunta salva pode ser recuperada e sai do cache`() {
        val original = pergunta()

        QuestionCacheManager.salvarPergunta(context, original)
        assertEquals(1, QuestionCacheManager.quantidade(context))

        val obtida = QuestionCacheManager.obterProximaPergunta(context)

        assertEquals(original.pergunta, obtida?.pergunta)
        assertEquals(0, QuestionCacheManager.quantidade(context))
    }

    @Test
    fun `pergunta duplicada com espacamento e caixa diferentes e ignorada`() {
        QuestionCacheManager.salvarPergunta(context, pergunta(texto = "Qual e a capital do Brasil?"))
        QuestionCacheManager.salvarPergunta(context, pergunta(texto = "  qual E a capital do brasil?  "))

        assertEquals(1, QuestionCacheManager.quantidade(context))
    }

    @Test
    fun `pergunta invalida nao e salva`() {
        val invalida = pergunta().copy(opcoes = listOf("A", "B"))

        QuestionCacheManager.salvarPergunta(context, invalida)

        assertEquals(0, QuestionCacheManager.quantidade(context))
    }

    @Test
    fun `pergunta ja usada hoje nao e salva de novo`() {
        val usada = pergunta()

        QuestionCacheManager.marcarComoUsada(context, usada)
        assertTrue(QuestionCacheManager.foiUsadaHoje(context, usada))

        QuestionCacheManager.salvarPergunta(context, usada)

        assertEquals(0, QuestionCacheManager.quantidade(context))
    }

    @Test
    fun `obterProximaPergunta prioriza a area preferida`() {
        QuestionCacheManager.salvarPergunta(
            context,
            pergunta(texto = "Pergunta de matemática?", area = "Matemática")
        )
        QuestionCacheManager.salvarPergunta(
            context,
            pergunta(texto = "Pergunta de humanas?", area = "Ciências Humanas")
        )

        val obtida = QuestionCacheManager.obterProximaPergunta(context, "Ciências Humanas")

        assertEquals("Pergunta de humanas?", obtida?.pergunta)
        assertEquals(1, QuestionCacheManager.quantidade(context))
    }

    @Test
    fun `cache com JSON corrompido e limpo automaticamente`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUESTIONS, "{ isto nao e um json valido")
            .apply()

        val lista = QuestionCacheManager.obterPerguntas(context)

        assertTrue(lista.isEmpty())
        assertEquals(0, QuestionCacheManager.quantidade(context))
    }

    @Test
    fun `cache respeita o limite maximo removendo as perguntas mais antigas`() {
        repeat(31) { indice ->
            QuestionCacheManager.salvarPergunta(context, pergunta(texto = "Pergunta número $indice?"))
        }

        val restantes = QuestionCacheManager.obterPerguntas(context)

        assertEquals(30, restantes.size)
        assertTrue(restantes.none { it.pergunta == "Pergunta número 0?" })
        assertTrue(restantes.any { it.pergunta == "Pergunta número 30?" })
    }

    @Test
    fun `virada de dia libera perguntas ja usadas`() {
        val usada = pergunta()
        QuestionCacheManager.marcarComoUsada(context, usada)
        assertTrue(QuestionCacheManager.foiUsadaHoje(context, usada))

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USED_DATE, "2000-01-01")
            .apply()

        assertFalse(QuestionCacheManager.foiUsadaHoje(context, usada))
    }

    @Test
    fun `cache vazio nao devolve pergunta`() {
        assertNull(QuestionCacheManager.obterProximaPergunta(context))
    }
}
