# Stuble

Stuble é um aplicativo Android nativo (Kotlin) que ajuda estudantes a se prepararem para
vestibulares brasileiros gerando um fluxo infinito de questões de múltipla escolha por IA
(Gemini), acompanhando progresso/sequência de estudos diária e gamificando o hábito de estudar
(XP, níveis, conquistas e desafios diários).

Projeto de TCC — `applicationId`: `com.company.stuble`.

## Funcionalidades

- **Questões geradas por IA**: cada questão é criada na hora pelo Gemini (`gemini-2.5-flash`),
  em português, com 4 alternativas, explicação da resposta correta e nível de dificuldade
  ajustável (Básico / Intermediário / Avançado / Adaptativo).
- **Cache local de perguntas**: um pré-carregamento em segundo plano mantém perguntas prontas por
  área, evitando tela de carregamento a cada questão e garantindo que o app continue funcionando
  mesmo com falhas temporárias de rede ou da API.
- **Dificuldade adaptativa**: quando o modo "Adaptativo" está ativo, o app sobe a dificuldade após
  3 acertos seguidos e desce após 2 erros seguidos.
- **Progresso e sequência (ofensiva)**: meta diária de 20 questões, com contagem de sequência de
  dias consecutivos.
- **Gamificação**: XP, níveis, estatísticas por área, calendário semanal, conquistas e um desafio
  diário que roda entre três tipos (responder 10 questões, acertar 7, manter sequência de 3).
- **Perfil personalizado**: onboarding que registra ano escolar, área de interesse, curso
  desejado, dificuldade preferida e matérias de maior dificuldade — usado para calibrar as
  perguntas geradas.
- **Notificações diárias**: lembrete às 20h (via WorkManager) para manter a sequência de estudos.
- **Login**: e-mail/senha e Google Sign-In, via Firebase Authentication.

## Arquitetura

### Fluxo de telas

```
LoginActivity (launcher)
    -> CadastroActivity (cadastro por e-mail)
    -> PersonalizacaoActivity (onboarding, grava um PerfilEstudante)
    -> MainActivity (BottomNavigationView: HomeFragment / SearchFragment / ProfileFragment)
        -> LoadingActivity (splash curto, repassa filtro de área / modo "treino livre")
            -> QuizActivity (loop de perguntas)
                -> ExplanationActivity
                -> MissionCompleteActivity
```

### Pipeline de geração de questões (`data/`)

A `QuizActivity` nunca chama a rede diretamente — tudo passa por `QuizRepository`:

1. `QuizRepository.obterProximaPergunta()` primeiro consulta o `QuestionCacheManager` (cache local
   pré-carregado, com deduplicação e expiração diária).
2. Em caso de cache miss, chama `GeminiQuestionService.gerarPergunta()`, que faz um POST direto
   (via OkHttp + Gson, sem SDK oficial) para o endpoint `generateContent` do `gemini-2.5-flash`,
   com um `responseSchema` estrito (força exatamente 4 alternativas, índice de 0 a 3, textos em
   português).
3. `QuestionParser` faz o parsing defensivo da resposta do modelo — tolera cercas de markdown,
   nomes de campo alternativos (`opcoes`/`opções`/`alternativas`, `correta`/`resposta`/etc.),
   resposta correta codificada por letra ("A"/"B"/...) e JSON truncado/malformado, antes de
   desistir e lançar uma exceção.
4. `QuizRepository.gerarComRetry()` tenta novamente uma vez em erros transitórios (`IOException`,
   ou HTTP 500/503) e rejeita uma questão repetida no mesmo dia. Em caso de falha, cai para
   qualquer questão em cache antes de mostrar erro na tela.
5. `precarregarUmaPergunta()` é chamado oportunisticamente para manter o cache cheio (≥ 2 questões
   por área) em segundo plano.

`QuizStateManager` persiste a questão em andamento (por dia/filtro/modo treino-livre) para
sobreviver à morte do processo sem gastar uma nova chamada de API.

### Armazenamento local por usuário

Não há banco de dados de backend para o estado do app — tudo além da autenticação fica em
`SharedPreferences`, isolado por usuário do Firebase. Cada manager em `data/`
(`ProgressManager`, `PerfilManager`, `GamificacaoManager`, `DificuldadeAdaptativaManager`,
`QuizStateManager`) segue o mesmo padrão: um `object` com uma constante `PREFIXO_PREFS`, uma
função privada que lê `FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"`, e um
helper `prefs(context)` que monta `PREFIXO_PREFS + uid` como nome do arquivo de preferências.

- `ProgressManager` — contagem diária de questões (meta de 20/dia) e lógica de sequência
  ("ofensiva"), com corte na virada do dia.
- `GamificacaoManager` — XP, níveis, estatísticas por área, calendário semanal, conquistas e o
  desafio diário (rotaciona por dia do ano).
- `DificuldadeAdaptativaManager` — acerto/erro consecutivo para subir/descer a dificuldade
  efetiva quando o modo preferido é "Adaptativo".
- `PerfilManager` — perfil de onboarding (`PerfilEstudante`).

### Notificações

`NotificationScheduler.scheduleDaily()` (chamado pela `MainActivity` após a permissão
`POST_NOTIFICATIONS` ser concedida) agenda um `PeriodicWorkRequest` único do WorkManager que
dispara o `DailyNotificationWorker` uma vez por dia às 20h.

## Stack técnica

- **Linguagem**: Kotlin, Android View system (sem Compose)
- **IA**: Gemini API (`gemini-2.5-flash`) via chamadas REST diretas (OkHttp + Gson)
- **Backend/auth**: Firebase Authentication (e-mail/senha + Google Sign-In) e Firebase Realtime
  Database
- **Background work**: WorkManager
- **Imagens**: Glide (fotos de perfil), Lottie (animações)
- **Build**: Gradle Kotlin DSL, AGP 8.13.2, Kotlin 2.0.21
- **Testes**: JUnit 4, MockK e Robolectric para os testes unitários da camada `data`

## Como rodar

### Pré-requisitos

- Android Studio (o projeto já inclui `app/google-services.json`)
- Uma chave de API do Gemini (https://aistudio.google.com/)

### Configuração da chave do Gemini

`GEMINI_API_KEY` é lida em `app/build.gradle.kts` via `project.findProperty("GEMINI_API_KEY")` e
injetada em `BuildConfig.GEMINI_API_KEY`. Ela **não** vem de `local.properties` nem de um
`secrets.properties` versionado — precisa ser passada como propriedade do Gradle. A forma mais
simples é adicionar uma linha ao seu `local.properties` (arquivo local, fora do controle de
versão):

```properties
GEMINI_API_KEY=sua_chave_aqui
```

Sem isso, o build compila normalmente, mas grava a string literal `"null"` em `BuildConfig`, e
toda chamada à API do Gemini falha em tempo de execução.

### Build e execução

```bash
./gradlew assembleDebug            # gera o APK de debug
./gradlew installDebug              # instala em um dispositivo/emulador conectado
```

### Testes

```bash
./gradlew test                                          # testes unitários (JVM, app/src/test)
./gradlew test --tests "com.company.stuble.data.QuestionParserTest"   # uma classe específica
./gradlew connectedAndroidTest                          # testes instrumentados (dispositivo/emulador)
./gradlew lint                                           # lint do Android
```

Não há ktlint/detekt configurado — `lint` é a única checagem estática integrada ao Gradle.

## Estrutura de pastas

```
app/src/main/java/com/company/stuble/
├── data/                 # repositório de questões, managers de progresso/perfil/gamificação
├── model/                # data classes (Pergunta, PerfilEstudante)
├── notifications/        # WorkManager: agendamento e worker da notificação diária
├── *Activity.kt          # telas (login, cadastro, onboarding, quiz, explicação, etc.)
└── *Fragment.kt          # abas da MainActivity (Home, Busca, Perfil)
```

## Autores

Vitor Henrique Miranda Carvalho, Vitor Farias Oliveira e
Isabella de Oliveira Gimenes Veloso.
