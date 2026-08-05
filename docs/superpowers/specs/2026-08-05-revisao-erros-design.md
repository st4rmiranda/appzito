# Revisão de erros — design

## Contexto e objetivo

O Stuble hoje descarta a informação de que o usuário errou uma questão assim que ela sai da
tela (só é usada, na hora, para ajustar `DificuldadeAdaptativaManager` e dar o texto de
explicação). Esta feature guarda as questões erradas por usuário e permite refazê-las depois,
reforçando o conteúdo que o aluno realmente não sabe — alinhado ao propósito central do app.

## Não-objetivos

- Não gera questões novas sobre o mesmo tema (a escolha foi refazer a questão original).
- Não conta para XP, meta diária de 20 questões ou sequência/ofensiva.
- Não tem estado vazio dedicado na Home (o card simplesmente fica oculto quando não há erros).
- Não adiciona testes instrumentados/UI — segue a convenção atual do projeto (só unitários).

## Arquitetura e componentes

### `RevisaoErrosManager` (novo, `app/src/main/java/com/company/stuble/data/`)

Segue o mesmo padrão dos outros managers (`PREFIXO_PREFS` + uid do Firebase, um
`SharedPreferences` por usuário) e reaproveita a serialização Gson de `Pergunta` já usada pelo
`QuestionCacheManager`.

- `PREFIXO_PREFS = "revisao_erros_"`, `LIMITE = 20`
- `registrarErro(context, pergunta)`: valida a pergunta (4 alternativas, nenhuma em branco,
  `correta` em 0..3), ignora duplicata por texto normalizado (mesmo `normalizar()` do
  `QuestionCacheManager`), adiciona ao fim da lista e descarta a mais antiga (índice 0) se passar
  de 20 itens (FIFO).
- `obterErros(context): List<Pergunta>`: lê e desserializa; em caso de `JsonSyntaxException`,
  loga, limpa a chave corrompida e devolve lista vazia (mesmo padrão defensivo do
  `QuestionCacheManager.obterPerguntas`).
- `removerErro(context, pergunta)`: remove por texto normalizado; no-op seguro se a pergunta já
  não estiver mais na lista.
- `quantidade(context): Int`.

### Ponto de integração em `QuizActivity`

Em `verificarResposta()` (`QuizActivity.kt`, ramo `else` de `if (acertou)`, hoje por volta da
linha 395), adicionar uma chamada a `RevisaoErrosManager.registrarErro(this, pergunta)`. Nenhuma
outra mudança nessa Activity.

### Telas novas

1. **Card na `HomeFragment`**: visível apenas quando `RevisaoErrosManager.quantidade(context) > 0`
   ("Você tem N questões pra revisar"), atualizado em `onResume` como as demais estatísticas da
   Home. Toque abre `RevisaoErrosActivity`.
2. **`RevisaoErrosActivity`** (nova, `exported=false`): RecyclerView com `RevisaoErrosAdapter`
   listando enunciado (truncado) + área de cada erro. Recarrega a lista em `onResume`. Mostra uma
   mensagem amigável de lista vazia caso o usuário zere os erros enquanto está na tela. Toque num
   item abre `RevisaoQuestaoActivity`, passando a `Pergunta` serializada em JSON (Gson) como extra
   do Intent.
3. **`RevisaoQuestaoActivity`** (nova): mesma pergunta+4 alternativas+confirmar visual da
   `QuizActivity`, porém isolada — sem barra de progresso, sem chamadas de
   gamificação/XP/streak/`ProgressManager`.
   - **Acertou**: toast de sucesso, chama `RevisaoErrosManager.removerErro(...)`, `finish()`.
   - **Errou de novo**: mostra a explicação inline (mesmo card de explicação da `QuizActivity`),
     permanece na tela; a questão não precisa ser registrada de novo (já está na lista).

## Fluxo de dados

```
QuizActivity erra a questão
    -> RevisaoErrosManager.registrarErro (valida, dedup, cap em 20)
    -> HomeFragment.onResume mostra o card se quantidade > 0
    -> RevisaoErrosActivity lista (RevisaoErrosManager.obterErros)
    -> toque no item -> RevisaoQuestaoActivity (Pergunta via Intent/Gson)
    -> acerto -> RevisaoErrosManager.removerErro -> finish()
    -> RevisaoErrosActivity.onResume recarrega a lista atualizada
```

## Tratamento de erros

- JSON corrompido no SharedPreferences: recuperação automática (limpa e retorna vazio), igual ao
  `QuestionCacheManager`.
- Pergunta inválida vinda da IA: rejeitada em `registrarErro`, nunca entra na lista.
- Duplicata (mesmo texto normalizado): ignorada, não cria segunda entrada.
- Corrida entre lista e remoção (pergunta já removida quando o usuário confirma a resposta):
  `removerErro` é no-op seguro, não lança exceção.

## Testes

`RevisaoErrosManagerTest` (novo, mesmo padrão de `QuestionCacheManagerTest`: `@RunWith(RobolectricTestRunner::class)`
+ MockK mockando `FirebaseAuth.getInstance()`):

- `registrarErro` adiciona e persiste.
- Duplicata normalizada é ignorada.
- Pergunta inválida não é salva.
- Limite de 20 descarta a mais antiga (FIFO).
- `removerErro` remove a pergunta certa e é no-op se ela não existir.
- Cache corrompido se recupera sozinho (lista vazia, sem crash).

Sem testes de UI/instrumentados novos.
