# Revisão de Erros Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a student store questions they answered wrong and retry them later from a dedicated screen, without those retries affecting XP, daily goal, or streak.

**Architecture:** A new per-user `RevisaoErrosManager` (SharedPreferences, same pattern as `QuestionCacheManager`) stores up to 20 missed `Pergunta` objects. `QuizActivity` writes to it on a wrong answer. Two new Activities read/consume it: `RevisaoQuestaoActivity` (retry one question) and `RevisaoErrosActivity` (list of missed questions), reached from a new card on `HomeFragment`.

**Tech Stack:** Kotlin, Android Views (no Compose), Gson (existing dependency) for `Pergunta` serialization, JUnit + MockK + Robolectric for the manager's unit tests (same as `QuestionCacheManagerTest`).

## Global Constraints

- Per-user storage: `SharedPreferences` name = `PREFIXO_PREFS + uid`, where `uid` = `FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"` (exact pattern used by every manager in `app/src/main/java/com/company/stuble/data/`).
- Max 20 stored wrong questions, FIFO eviction of the oldest when the 21st is added.
- Retrying a question (right or wrong) must NOT call `GamificacaoManager`, `ProgressManager`, or `DificuldadeAdaptativaManager` — no XP, no daily-goal count, no streak change.
- No new instrumented/UI tests — this project only has unit tests under `app/src/test`, matching `QuestionCacheManagerTest`'s Robolectric + MockK setup.
- minSdk 24 / compileSdk 34, no Jetpack Compose, follow existing hardcoded-hex-color XML style (no `colors.xml` resources are used elsewhere in this project).
- Before running any Gradle command, set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (the system JAVA_HOME on this machine points to an invalid path).

---

### Task 1: `RevisaoErrosManager` data layer

**Files:**
- Create: `app/src/main/java/com/company/stuble/data/RevisaoErrosManager.kt`
- Create: `app/src/test/java/com/company/stuble/data/RevisaoErrosManagerTest.kt`

**Interfaces:**
- Consumes: `com.company.stuble.model.Pergunta(pergunta: String, opcoes: List<String>, correta: Int, explicacao: String, area: String)` (existing).
- Produces:
  ```kotlin
  object RevisaoErrosManager {
      fun registrarErro(context: Context, pergunta: Pergunta)
      fun obterErros(context: Context): List<Pergunta>
      fun removerErro(context: Context, pergunta: Pergunta)
      fun quantidade(context: Context): Int
      fun limparTudo(context: Context)
  }
  ```

- [ ] **Step 1: Write the full test file**

Create `app/src/test/java/com/company/stuble/data/RevisaoErrosManagerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to confirm it fails to compile**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test --console=plain
```

Expected: build fails — `RevisaoErrosManager` is unresolved (the class doesn't exist yet).

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/company/stuble/data/RevisaoErrosManager.kt`:

```kotlin
package com.company.stuble.data

import android.content.Context
import android.util.Log
import com.company.stuble.model.Pergunta
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.util.Locale

object RevisaoErrosManager {

    private const val TAG = "RevisaoErrosManager"
    private const val PREFIXO_PREFS = "revisao_erros_"
    private const val KEY_ERROS = "questoes_erradas"
    private const val LIMITE = 20

    private val gson = Gson()

    private fun uid(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFIXO_PREFS + uid(), Context.MODE_PRIVATE)

    @Synchronized
    fun registrarErro(context: Context, pergunta: Pergunta) {
        if (!perguntaValida(pergunta)) {
            Log.w(TAG, "Pergunta inválida ignorada.")
            return
        }

        val lista = obterErros(context).toMutableList()

        val repetida = lista.any {
            normalizar(it.pergunta) == normalizar(pergunta.pergunta)
        }

        if (repetida) {
            return
        }

        lista.add(pergunta)

        while (lista.size > LIMITE) {
            lista.removeAt(0)
        }

        salvarLista(context, lista)
    }

    fun obterErros(context: Context): List<Pergunta> {
        val json = prefs(context).getString(KEY_ERROS, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<Pergunta>>() {}.type

            gson.fromJson<List<Pergunta>>(json, type)
                ?.filter(::perguntaValida)
                ?: emptyList()
        } catch (erro: JsonSyntaxException) {
            Log.e(TAG, "Cache de erros inválido. Será limpo.", erro)
            limparTudo(context)
            emptyList()
        } catch (erro: Exception) {
            Log.e(TAG, "Erro ao ler as questões erradas.", erro)
            emptyList()
        }
    }

    @Synchronized
    fun removerErro(context: Context, pergunta: Pergunta) {
        val lista = obterErros(context).filterNot {
            normalizar(it.pergunta) == normalizar(pergunta.pergunta)
        }

        salvarLista(context, lista)
    }

    fun quantidade(context: Context): Int = obterErros(context).size

    fun limparTudo(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun salvarLista(context: Context, lista: List<Pergunta>) {
        prefs(context)
            .edit()
            .putString(KEY_ERROS, gson.toJson(lista))
            .apply()
    }

    private fun perguntaValida(pergunta: Pergunta): Boolean {
        return pergunta.pergunta.isNotBlank() &&
            pergunta.opcoes.size == 4 &&
            pergunta.opcoes.none { it.isBlank() } &&
            pergunta.correta in 0..3 &&
            pergunta.explicacao.isNotBlank()
    }

    private fun normalizar(texto: String): String {
        return texto
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test --console=plain
```

Expected: `BUILD SUCCESSFUL`, and `app\build\test-results\testDebugUnitTest\TEST-com.company.stuble.data.RevisaoErrosManagerTest.xml` shows 8 tests, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/company/stuble/data/RevisaoErrosManager.kt app/src/test/java/com/company/stuble/data/RevisaoErrosManagerTest.kt
git commit -m "feat: adiciona RevisaoErrosManager para guardar questoes erradas"
```

---

### Task 2: Hook wrong answers into `QuizActivity`

**Files:**
- Modify: `app/src/main/java/com/company/stuble/QuizActivity.kt`

**Interfaces:**
- Consumes: `RevisaoErrosManager.registrarErro(context: Context, pergunta: Pergunta)` (Task 1).

- [ ] **Step 1: Add the import**

In `app/src/main/java/com/company/stuble/QuizActivity.kt`, in the import block (currently starting at line 15 with `import com.company.stuble.data.DificuldadeAdaptativaManager`), add:

```kotlin
import com.company.stuble.data.RevisaoErrosManager
```

- [ ] **Step 2: Register the wrong answer**

Find this block inside `verificarResposta()`:

```kotlin
        } else {
            modoExplicacaoAtivo = true
            txtExplanation.text = pergunta.explicacao
            cardExplanation.isVisible = true
            btnConfirm.text = "CONTINUAR"

            quizScrollView.post {
                quizScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
```

Replace it with:

```kotlin
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
```

- [ ] **Step 3: Verify the project still compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/company/stuble/QuizActivity.kt
git commit -m "feat: registra questao errada na revisao de erros"
```

---

### Task 3: `RevisaoQuestaoActivity` (retry a single question)

**Files:**
- Create: `app/src/main/res/layout/activity_revisao_questao.xml`
- Create: `app/src/main/java/com/company/stuble/RevisaoQuestaoActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `com.company.stuble.model.Pergunta`, `RevisaoErrosManager.removerErro(context, pergunta)` (Task 1).
- Produces: `RevisaoQuestaoActivity` with `companion object { const val EXTRA_PERGUNTA_JSON: String }`. Callers must launch it with `Intent(context, RevisaoQuestaoActivity::class.java).putExtra(RevisaoQuestaoActivity.EXTRA_PERGUNTA_JSON, Gson().toJson(pergunta))`.

- [ ] **Step 1: Create the layout**

Create `app/src/main/res/layout/activity_revisao_questao.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FAFBFF">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="24dp">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnVoltarRevisaoQuestao"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginTop="12dp"
            android:contentDescription="Voltar"
            app:icon="@drawable/ic_back"
            app:iconTint="#2D3243"
            app:iconGravity="textStart"
            app:backgroundTint="#FFFFFF"
            app:strokeColor="#E0E0E0"
            app:strokeWidth="1dp"
            app:cornerRadius="24dp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="Refazendo questão"
            android:textColor="#2D3243"
            android:textSize="14sp"
            android:textStyle="bold" />

        <androidx.core.widget.NestedScrollView
            android:id="@+id/revisaoQuestaoScrollView"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:layout_marginTop="24dp"
            android:layout_marginBottom="12dp"
            android:fillViewport="true">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <com.google.android.material.card.MaterialCardView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="24dp"
                    app:cardBackgroundColor="#FFFFFF"
                    app:cardCornerRadius="24dp"
                    app:cardElevation="2dp"
                    app:strokeWidth="0dp">

                    <TextView
                        android:id="@+id/txtRevisaoQuestaoEnunciado"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:padding="24dp"
                        android:lineSpacingExtra="4dp"
                        android:text="Carregando enunciado..."
                        android:textColor="#2D3243"
                        android:textSize="18sp" />
                </com.google.android.material.card.MaterialCardView>

                <RadioGroup
                    android:id="@+id/rgRevisaoOptions"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp">

                    <RadioButton
                        android:id="@+id/revisaoOpt1"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="12dp"
                        android:background="@drawable/bg_opcao_quiz"
                        android:button="@null"
                        android:padding="18dp"
                        android:text="Alternativa A"
                        android:textColor="#2D3243"
                        android:textSize="16sp" />

                    <RadioButton
                        android:id="@+id/revisaoOpt2"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="12dp"
                        android:background="@drawable/bg_opcao_quiz"
                        android:button="@null"
                        android:padding="18dp"
                        android:text="Alternativa B"
                        android:textColor="#2D3243"
                        android:textSize="16sp" />

                    <RadioButton
                        android:id="@+id/revisaoOpt3"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="12dp"
                        android:background="@drawable/bg_opcao_quiz"
                        android:button="@null"
                        android:padding="18dp"
                        android:text="Alternativa C"
                        android:textColor="#2D3243"
                        android:textSize="16sp" />

                    <RadioButton
                        android:id="@+id/revisaoOpt4"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginBottom="12dp"
                        android:background="@drawable/bg_opcao_quiz"
                        android:button="@null"
                        android:padding="18dp"
                        android:text="Alternativa D"
                        android:textColor="#2D3243"
                        android:textSize="16sp" />
                </RadioGroup>

                <com.google.android.material.card.MaterialCardView
                    android:id="@+id/cardRevisaoExplicacao"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="24dp"
                    android:visibility="gone"
                    app:cardBackgroundColor="#FFF0F0"
                    app:cardCornerRadius="18dp"
                    app:strokeColor="#FFCDCD"
                    app:strokeWidth="1dp"
                    app:cardElevation="0dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="20dp">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Resposta Incorreta ❌"
                            android:textColor="#C62828"
                            android:textSize="15sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/txtRevisaoQuestaoExplicacao"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="8dp"
                            android:lineSpacingExtra="3dp"
                            android:text="Explicação..."
                            android:textColor="#5D4037"
                            android:textSize="14sp" />
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>

            </LinearLayout>
        </androidx.core.widget.NestedScrollView>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnRevisaoConfirmar"
            android:layout_width="match_parent"
            android:layout_height="65dp"
            android:text="CONFIRMAR RESPOSTA"
            android:textSize="16sp"
            android:textStyle="bold"
            app:backgroundTint="#6C63FF"
            app:cornerRadius="16dp" />

    </LinearLayout>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Create the Activity**

Create `app/src/main/java/com/company/stuble/RevisaoQuestaoActivity.kt`:

```kotlin
package com.company.stuble

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.company.stuble.data.RevisaoErrosManager
import com.company.stuble.model.Pergunta
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson

class RevisaoQuestaoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERGUNTA_JSON = "PERGUNTA_JSON"
    }

    private lateinit var pergunta: Pergunta
    private lateinit var txtEnunciado: TextView
    private lateinit var rgOptions: RadioGroup
    private lateinit var cardExplicacao: MaterialCardView
    private lateinit var txtExplicacao: TextView
    private lateinit var btnConfirmar: MaterialButton

    private var respostaContabilizada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_revisao_questao)

        val json = intent.getStringExtra(EXTRA_PERGUNTA_JSON)
        if (json == null) {
            finish()
            return
        }

        pergunta = Gson().fromJson(json, Pergunta::class.java)

        vincularViews()
        exibirPergunta()

        findViewById<MaterialButton>(R.id.btnVoltarRevisaoQuestao)
            .setOnClickListener { finish() }

        btnConfirmar.setOnClickListener {
            if (respostaContabilizada) {
                finish()
            } else {
                verificarResposta()
            }
        }
    }

    private fun vincularViews() {
        txtEnunciado = findViewById(R.id.txtRevisaoQuestaoEnunciado)
        rgOptions = findViewById(R.id.rgRevisaoOptions)
        cardExplicacao = findViewById(R.id.cardRevisaoExplicacao)
        txtExplicacao = findViewById(R.id.txtRevisaoQuestaoExplicacao)
        btnConfirmar = findViewById(R.id.btnRevisaoConfirmar)
    }

    private fun exibirPergunta() {
        txtEnunciado.text = pergunta.pergunta
        cardExplicacao.isVisible = false

        val opcoes = listOf(
            findViewById<RadioButton>(R.id.revisaoOpt1),
            findViewById<RadioButton>(R.id.revisaoOpt2),
            findViewById<RadioButton>(R.id.revisaoOpt3),
            findViewById<RadioButton>(R.id.revisaoOpt4)
        )

        opcoes.forEachIndexed { indice, radioButton ->
            radioButton.text = pergunta.opcoes.getOrNull(indice).orEmpty()
            radioButton.tag = indice
        }
    }

    private fun verificarResposta() {
        val idSelecionado = rgOptions.checkedRadioButtonId

        if (idSelecionado == -1) {
            Toast.makeText(this, "Selecione uma alternativa.", Toast.LENGTH_SHORT).show()
            return
        }

        val indiceSelecionado = findViewById<RadioButton>(idSelecionado).tag as Int
        val acertou = indiceSelecionado == pergunta.correta

        respostaContabilizada = true

        if (acertou) {
            RevisaoErrosManager.removerErro(this, pergunta)
            Toast.makeText(this, "Resposta correta! ✅", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            txtExplicacao.text = pergunta.explicacao
            cardExplicacao.isVisible = true
            btnConfirmar.text = "FECHAR"
        }
    }
}
```

- [ ] **Step 3: Register the Activity in the manifest**

In `app/src/main/AndroidManifest.xml`, right after the `.QuizActivity` entry:

```xml
        <activity
            android:name=".QuizActivity"
            android:exported="false" />

        <activity
            android:name=".RevisaoQuestaoActivity"
            android:exported="false" />
```

- [ ] **Step 4: Verify the project compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/layout/activity_revisao_questao.xml app/src/main/java/com/company/stuble/RevisaoQuestaoActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: adiciona tela de refazer questao errada"
```

---

### Task 4: `RevisaoErrosActivity` (list of missed questions)

**Files:**
- Create: `app/src/main/res/layout/item_erro_revisao.xml`
- Create: `app/src/main/res/layout/activity_revisao_erros.xml`
- Create: `app/src/main/java/com/company/stuble/RevisaoErrosAdapter.kt`
- Create: `app/src/main/java/com/company/stuble/RevisaoErrosActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `RevisaoErrosManager.obterErros(context)` (Task 1), `RevisaoQuestaoActivity.EXTRA_PERGUNTA_JSON` (Task 3).
- Produces: `RevisaoErrosActivity`, launchable with no extras: `Intent(context, RevisaoErrosActivity::class.java)`.

- [ ] **Step 1: Create the list item layout**

Create `app/src/main/res/layout/item_erro_revisao.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="14dp"
    app:cardBackgroundColor="#FFFFFF"
    app:cardCornerRadius="18dp"
    app:cardElevation="3dp"
    app:strokeColor="#E5E7EB"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="18dp">

        <TextView
            android:id="@+id/txtErroArea"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Matemática"
            android:textColor="#DC2626"
            android:textSize="12sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/txtErroEnunciado"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:ellipsize="end"
            android:maxLines="3"
            android:text="Enunciado da questão…"
            android:textColor="#2D3243"
            android:textSize="15sp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Create the list screen layout**

Create `app/src/main/res/layout/activity_revisao_erros.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FAFBFF">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="24dp">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnVoltarRevisao"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginTop="12dp"
            android:contentDescription="Voltar"
            app:icon="@drawable/ic_back"
            app:iconTint="#2D3243"
            app:iconGravity="textStart"
            app:backgroundTint="#FFFFFF"
            app:strokeColor="#E0E0E0"
            app:strokeWidth="1dp"
            app:cornerRadius="24dp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="Revisão de erros"
            android:textColor="#0F172A"
            android:textSize="24sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="Refaça as questões que você errou até acertar."
            android:textColor="#64748B"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/txtRevisaoVazia"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="40dp"
            android:gravity="center"
            android:text="Nenhuma questão para revisar. 🎉"
            android:textColor="#64748B"
            android:textSize="16sp"
            android:visibility="gone" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerRevisaoErros"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:layout_marginTop="20dp"
            android:clipToPadding="false"
            android:paddingBottom="16dp" />
    </LinearLayout>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 3: Create the adapter**

Create `app/src/main/java/com/company/stuble/RevisaoErrosAdapter.kt`:

```kotlin
package com.company.stuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.company.stuble.model.Pergunta

class RevisaoErrosAdapter(
    private val aoClicar: (Pergunta) -> Unit
) : RecyclerView.Adapter<RevisaoErrosAdapter.ViewHolder>() {

    private var itens: List<Pergunta> = emptyList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtErroArea: TextView = view.findViewById(R.id.txtErroArea)
        val txtErroEnunciado: TextView = view.findViewById(R.id.txtErroEnunciado)
    }

    fun atualizarLista(novaLista: List<Pergunta>) {
        itens = novaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_erro_revisao, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = itens.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pergunta = itens[position]

        holder.txtErroArea.text = pergunta.area
        holder.txtErroEnunciado.text = pergunta.pergunta

        holder.itemView.setOnClickListener {
            aoClicar(pergunta)
        }
    }
}
```

- [ ] **Step 4: Create the Activity**

Create `app/src/main/java/com/company/stuble/RevisaoErrosActivity.kt`:

```kotlin
package com.company.stuble

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.company.stuble.data.RevisaoErrosManager
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson

class RevisaoErrosActivity : AppCompatActivity() {

    private lateinit var adapter: RevisaoErrosAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var txtVazia: TextView
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_revisao_erros)

        adapter = RevisaoErrosAdapter { pergunta ->
            val intent = Intent(this, RevisaoQuestaoActivity::class.java).apply {
                putExtra(RevisaoQuestaoActivity.EXTRA_PERGUNTA_JSON, gson.toJson(pergunta))
            }
            startActivity(intent)
        }

        recycler = findViewById(R.id.recyclerRevisaoErros)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        txtVazia = findViewById(R.id.txtRevisaoVazia)

        findViewById<MaterialButton>(R.id.btnVoltarRevisao)
            .setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        carregarLista()
    }

    private fun carregarLista() {
        val erros = RevisaoErrosManager.obterErros(this)

        adapter.atualizarLista(erros)
        recycler.isVisible = erros.isNotEmpty()
        txtVazia.isVisible = erros.isEmpty()
    }
}
```

- [ ] **Step 5: Register the Activity in the manifest**

In `app/src/main/AndroidManifest.xml`, right after the `.RevisaoQuestaoActivity` entry added in Task 3:

```xml
        <activity
            android:name=".RevisaoQuestaoActivity"
            android:exported="false" />

        <activity
            android:name=".RevisaoErrosActivity"
            android:exported="false" />
```

- [ ] **Step 6: Verify the project compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/res/layout/item_erro_revisao.xml app/src/main/res/layout/activity_revisao_erros.xml app/src/main/java/com/company/stuble/RevisaoErrosAdapter.kt app/src/main/java/com/company/stuble/RevisaoErrosActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: adiciona tela de lista da revisao de erros"
```

---

### Task 5: Entry point on `HomeFragment`

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/company/stuble/HomeFragment.kt`

**Interfaces:**
- Consumes: `RevisaoErrosManager.quantidade(context)` (Task 1), `RevisaoErrosActivity` (Task 4, launched with no extras).

- [ ] **Step 1: Add the card to the layout**

In `app/src/main/res/layout/fragment_home.xml`, find the `LinearLayout` that holds the "Questões"/"Missões" row — it ends right before the "⭐ Seu nível" card:

```xml
                </com.google.android.material.card.MaterialCardView>
            </LinearLayout>

            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                app:cardBackgroundColor="#FFFFFF"
                app:cardCornerRadius="27dp"
                app:cardElevation="0dp"
                app:strokeColor="#E2E8F0"
                app:strokeWidth="1dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="21dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="⭐ Seu nível"
```

Insert the new card between the two, right after the closing `</LinearLayout>` of the Questões/Missões row and before the "Seu nível" `MaterialCardView`:

```xml
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardRevisaoErros"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="18dp"
                android:visibility="gone"
                app:cardBackgroundColor="#FEF2F2"
                app:cardCornerRadius="22dp"
                app:cardElevation="0dp"
                app:strokeColor="#FECACA"
                app:strokeWidth="1dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:gravity="center_vertical"
                    android:orientation="horizontal"
                    android:padding="18dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="📝"
                        android:textSize="28sp" />

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:layout_weight="1"
                        android:orientation="vertical">

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="Revise o que você errou"
                            android:textColor="#991B1B"
                            android:textSize="15sp"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/txtRevisaoErrosResumo"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="2dp"
                            android:text="0 questões para revisar"
                            android:textColor="#B91C1C"
                            android:textSize="13sp" />
                    </LinearLayout>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnRevisarErros"
                        android:layout_width="wrap_content"
                        android:layout_height="44dp"
                        android:text="REVISAR"
                        android:textColor="#FFFFFF"
                        android:textSize="12sp"
                        android:textStyle="bold"
                        app:backgroundTint="#DC2626"
                        app:cornerRadius="14dp" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Wire it up in `HomeFragment`**

In `app/src/main/java/com/company/stuble/HomeFragment.kt`, add the import next to the other `data` imports:

```kotlin
import com.company.stuble.data.RevisaoErrosManager
```

Add `androidx.core.view.isVisible` to the imports as well:

```kotlin
import androidx.core.view.isVisible
```

In `onViewCreated`, right after the existing `btnStartQuestions` click listener block, add:

```kotlin
        view.findViewById<Button>(R.id.btnRevisarErros)
            .setOnClickListener {
                startActivity(
                    Intent(requireContext(), RevisaoErrosActivity::class.java)
                )
            }
```

In `atualizarTela(view: View)`, add a call to a new function at the end of the existing chain:

```kotlin
        atualizarNivel(view)
        atualizarDesafio(view)
        atualizarCalendario(view)
        atualizarEstatisticas(view)
        atualizarConquistas(view)
        atualizarCardRevisaoErros(view)
```

Add the new private function next to `atualizarConquistas`:

```kotlin
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
```

- [ ] **Step 3: Verify the project compiles**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/res/layout/fragment_home.xml app/src/main/java/com/company/stuble/HomeFragment.kt
git commit -m "feat: adiciona card de revisao de erros na Home"
```

---

### Task 6: End-to-end verification

**Files:** none (manual verification only).

**Interfaces:** none.

- [ ] **Step 1: Run the full unit test suite**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test --console=plain
```

Expected: `BUILD SUCCESSFUL`, all suites green (including `RevisaoErrosManagerTest`).

- [ ] **Step 2: Run lint**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat lint --console=plain
```

Expected: completes without new errors introduced by the files touched in this plan (pre-existing warnings elsewhere in the project are out of scope).

- [ ] **Step 3: Manual walkthrough on a device/emulator**

Install the debug build and confirm, in order:
1. Answer a question wrong in `QuizActivity` → go back to `MainActivity` → the "Revise o que você errou" card appears on the Home tab with "1 questão para revisar".
2. Tap "REVISAR" → `RevisaoErrosActivity` opens and lists the missed question with its area and enunciado.
3. Tap the question → `RevisaoQuestaoActivity` opens showing the same enunciado and 4 alternatives.
4. Pick the wrong alternative → explanation card appears, button becomes "FECHAR".
5. Tap "FECHAR" → returns to the list; the question is still there (not removed).
6. Tap the question again, pick the correct alternative → success toast, screen closes, back on the list the question is gone.
7. With zero errors left, the empty-state message ("Nenhuma questão para revisar. 🎉") shows in `RevisaoErrosActivity`, and the Home card is hidden again after returning to `MainActivity`.
8. Confirm XP, daily-goal counter (Home progress bar), and streak on the Home tab did **not** change from any of the retries in this walkthrough.

Document the result of this manual walkthrough for the user (this environment has no attached device/emulator, so this step must be run by the user themselves).

- [ ] **Step 4: Final commit if anything was adjusted during manual verification**

```powershell
git add -A
git commit -m "fix: ajustes pos-verificacao manual da revisao de erros"
```

(Skip this step if the walkthrough required no code changes.)
