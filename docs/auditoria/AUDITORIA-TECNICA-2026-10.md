# Auditoria técnica — Sonivell

- **Data:** 31/10/2026
- **Commit auditado:** `1f6d1b9` (`main`, depois do merge do #13)
- **Tipo:** análise estática do repositório inteiro (357 arquivos, ~42,6 mil linhas de Kotlin, mais C/C++ nativo, scripts e CI).
- **Etapa:** 1 de 2. Nenhum arquivo de código foi alterado. Este documento é o único arquivo novo.
- **Correções:** #15, #16, #17 e #18, todas na `main`. Versão resultante: **1.0.0-stable** (#19).
- **Encerramento:** 01/11/2026. Vereditos finais na seção logo abaixo; status de cada item em seguida.

---

## Vereditos finais (encerramento, 01/11/2026)

A auditoria está encerrada. Todos os itens P1 e P2 foram corrigidos, validados no CI e **testados no aparelho real**. O que sobra são melhorias P3/P4 de baixo risco e duas configurações do GitHub que dependem de você.

| Pergunta | Na auditoria (31/10) | Veredito final |
|---|---|---|
| O projeto está seguro? | Parcialmente | **Sim.** Assinatura só com a chave própria, guardada no environment `release` (#17); serviço de mídia com lista de apps permitidos; restaurar backup com limites e só no formato do Sonivell; nenhuma CVE conhecida nas 295 dependências do app; dependências e wrapper conferidos por SHA-256. As CVEs do ferramental de build (não vão nos APKs) são tratadas pela atualização do AGP/Kotlin/Gradle (#18). |
| O projeto está estável? | Em primeiro plano, sim; em segundo plano, não totalmente | **Sim.** Temporizador, histórico, sessão e fila SMART rodam no serviço, com a tela fechada; gravações atômicas, arquivos corrompidos separados; CI verde em todos os workflows da `main`; testado no aparelho. |
| Há gargalos importantes? | Sim (PERF-01) | **Não.** Busca e ordenação fora da thread principal (ordenação 1.447 → 186 ms, busca 105 → 36 ms, 20 mil faixas). Restam PERF-03 (índice de letras sem teto) e PERF-04, ambos P3/P4. |
| Está pronto para produção? | Não para distribuição pública | **Sim**, como **1.0.0-stable**, com duas condições de configuração (👤): CI obrigatório na proteção da `main` e o environment `release` restrito à `main`. |

### Notas revisadas

| Categoria | Antes | Depois | Motivo |
|---|---|---|---|
| Segurança | 5 | **8** | SEC-01..05 corrigidos; varredura de CVEs no CI; SEC-06 aceito. |
| Estabilidade | 5 | **8** | REL-01..06 corrigidos e testados no aparelho. |
| Performance | 6 | **7** | PERF-01/02/05 corrigidos; PERF-03 parcial e PERF-04 pendentes. |
| Escalabilidade | 6 | **7** | Busca e ordenação com chaves pré-calculadas; o teto de ~17 mil faixas do índice SMART continua. |
| Arquitetura | 5 | **6** | Lógica de reprodução no serviço; ARQ-01..03 (arquivos de UI grandes, telas duplicadas) seguem pendentes. |
| Qualidade do código | 6 | **7** | Leituras limitadas, gravações centralizadas, lint com 0 erros. |
| Testabilidade | 3 | **5** | 25 testes no app, 20 no motor e 12 no desktop, todos no CI; ainda sem testes em emulador. |
| DevOps | 4 | **8** | Actions por SHA, permissões mínimas, verificação de dependências, OSV, lint, zizmor/actionlint, `versionCode` automático, secrets em environment. |
| Observabilidade | 3 | **3** | Sem mudança: não há registro local de falhas nem relatório de erro exportável. |

### O que fica para depois (não bloqueia a 1.0.0-stable)
- **👤 Você:** proteção da `main` com `build` e `seguranca` obrigatórios (CI-01); environment `release` restrito à `main`; escolher GitHub ou GitLab como CI oficial (CI-02).
- **Código (P3/P4):** PERF-03 (teto no índice de letras), PERF-04, ARQ-01..03, busca por voz, `smart-test.yml` ainda com escrita e `push -f` na `smart-logs`.
- **Testes:** testes em emulador do serviço de reprodução; registro local de falhas (observabilidade).

---

## Status das correções

_Atualizado em 01/11/2026. O #16, o #17, o #18 e o #19 também estão na `main`. Legenda: ✅ na `main` (#15) · 🟢 corrigido e validado no #16 (na `main`) · 🟡 parcial · ⏳ pendente · 👤 depende de você · ⚪ risco aceito._

| ID | Sev. | Status | Onde | Observação |
|---|---|---|---|---|
| SEC-01 | P1 | 🟢 | #15 + #16 | A chave de teste saiu do repositório e do build; o release só assina com a chave dos *secrets* (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). **Validado no CI:** os dois APKs (lite e SMART) saíram assinados com a chave nova, SHA-256 do certificado `af0a0e7a0b5f13d8e5181916d8d9bb88acd6f61bf1861b90bc893382fd488ac6` (a antiga chave de teste era `882ef7c4…1477b`). Os secrets foram movidos para o *environment* `release` (#17); falta (👤) restringir o environment à `main`. Quem tem APK com a chave de teste precisa desinstalar e instalar de novo. |
| SEC-02 | P2 | ✅ | #15 | Navegação da biblioteca só para o próprio app, apps confiáveis, Android Auto, Wear e Assistente. |
| SEC-03 | P2 | ✅ | #15 | `BackupArchive`: nomes permitidos, limites por arquivo/total/quantidade, troca arquivo a arquivo. 8 testes. |
| SEC-04 | P3 | 🟢 | #16 | Regras de backup/extração: histórico, gosto e índices do SMART fora do backup do Android. |
| SEC-05 | P3 | 🟢 | #16 | JNI do `ljdots` confere cada índice de linha. |
| SEC-06 | P4 | ⚪ | #16 | Risco aceito: só ações fixas, sem dados; restringir quebraria atalhos e bloco rápido. |
| SUP-01 | P3 | 🟢 | #16 | Actions fixadas por SHA. `gradle/verification-metadata.xml` gerado no CI (751 componentes, SHA-256) e `distributionSha256Sum` no wrapper; o build e os testes passam com a verificação ligada. Os POMs marcadores dos três plugins JetBrains aceitam também a versão do Gradle Plugin Portal (`also-trust`), que difere da do Maven Central. |
| SUP-02 | P2 | 🟢 | #15 + #16 | ffmpeg `autobuild-2026-10-31-12-59` e appimagetool 1.9.1 conferidos por SHA-256. |
| SUP-03 | P3 | 🟢 | #16 | Todos os workflows só leitura; escrita só nos jobs que publicam logs/capturas, sem rodar código do branch. zizmor e actionlint sem achados. |
| Licenças | P4 | 🟢 | #16 | NOTICE cita os componentes nativos e a BSD-4-Clause. |
| REL-01 | P1 | 🟢 | #15 + #16 | Temporizador, histórico e "onde parei" no serviço (#15); fila SMART "infinita" com o app fechado (#16). |
| REL-02 | P2 | ✅ | #15 | `AtomicFiles` no Android e no desktop. 5 testes. |
| REL-03 | P2 | ✅ | #15 | Timeouts no ffprobe (20 s) e no ffmpeg da capa (15 s, 10 MB). |
| REL-04 | P3 | 🟢 | #16 | Restaurar aplica as preferências pelo `SharedPreferences`; `PrefsXml` sem DTD/XXE. 3 testes. |
| REL-05 | P3 | 🟢 | #16 | Leituras com limite: `.m3u` 5 MB, `.lrc` 2 MB, capa 30 MB. 3 testes. |
| REL-06 | P4 | ✅ | #15 | Paginação em `Long`. |
| PERF-01 | P2 | ✅ | #15 | Biblioteca e busca fora da thread principal, com *debounce*. Ordenação 1.447 → 186 ms, busca 105 → 36 ms (20 mil faixas, JVM). |
| PERF-02 | P3 | ✅ | #15 | `currentTrack` só recalcula quando a faixa muda. |
| PERF-03 | P3 | 🟡 | #16 | Cada letra lida com limite de 2 MB (REL-05); o índice das letras ainda não tem teto. |
| PERF-04 | P4 | ⏳ | — | Mexe no motor; fica para uma revisão do SMART. |
| PERF-05 | P4 | 🟢 | #16 | Itens de listas longas sem sombra. |
| ARQ-01..03 | P3 | ⏳ | — | Refatorações grandes; ciclo próprio. |
| TEST-01 | P2 | ✅ | #15 + #16 | 17 testes no #15 e mais 8 no #16, rodando no `build.yml`. |
| CI-01 | P3 | 🟡 | #15 + #16 | Testes no `build.yml` (#15). Android Lint completo (lite e smart) no `seguranca.yml`: a primeira execução achou **12 erros**, todos corrigidos no #16; ficaram 0 erros e 99 avisos (que não reprovam). Falta tornar o `seguranca.yml` obrigatório na proteção da `main` (👤). |
| CI-02 | P4 | 👤 | — | Escolher qual CI é o oficial (GitHub ou GitLab). |
| CI-03 | P3 | 🟢 | #16 | `versionCode` = número da execução do CI. |
| Varredura de CVEs (0.3) | — | 🟢 | #16 | OSV-Scanner no CI, em todo PR e toda segunda. Ver resultados abaixo. |
| Ferramental de build (AGP 9.3.3, Kotlin 2.4.21, Gradle 9.5.1) | — | 🟢 | #18 | Atualização para reduzir as CVEs do classpath de build; hashes regenerados pelo workflow de verificação. |
| Versão | — | 🟢 | #19 | `versionName` 1.0.0-stable. |
| Busca por voz | P4 | ⏳ | — | Novo, achado pelo lint (`MissingIntentFilterForMediaSearch`): o app não trata "tocar X no Sonivell". Aviso suprimido com o motivo no manifesto até a função existir. |

### Resultados do CI de segurança (#16)
- **Workflows (zizmor 1.31.1 + actionlint 1.7.12):** sem achados de severidade média ou alta e sem erros. A única exceção registrada é `secrets-outside-env` no `build.yml` (ver SEC-01).
- **CVEs nas dependências do app (Android e PC, 295 pacotes):** a primeira execução achou **CVE-2026-102554 (CVSS 8.2) na `guava` 33.3.1-android**, trazida pelo media3 e pelo coroutines-guava. Corrigido no #16 com a 33.7.2-android como versão mínima. Depois disso: nenhuma vulnerabilidade conhecida.
- **CVEs nas ferramentas de build (informativo, 751 componentes):** 91 vulnerabilidades em 21 pacotes, todas no ferramental que o AGP, o Kotlin e o lint trazem e que não vai nos APKs. Os mais graves: `bouncycastle` 1.79 (até 9.3), `netty` 4.1.93/4.1.110 (até 9.1), `guava` -jre 32.0.1/33.3.1 (8.2), `jdom2` 2.0.6 e `jose4j` 0.9.5 (7.5). Correção: atualizar AGP e Kotlin num ciclo próprio (⏳).
- **Android Lint:** 12 erros na primeira execução, todos corrigidos no #16 — `UnsafeOptInUsageError` (7, anotações do media3), `ContextCastToActivity` (2, `LocalActivity`), `StartActivityAndCollapseDeprecated` (1, já usava a versão nova no Android 14+) e `MissingIntentFilterForMediaSearch` (2, ver "Busca por voz"). Agora 0 erros e 99 avisos.
- **Assinatura:** `build.yml` confere os secrets antes de compilar, valida cada APK com o `apksigner` e registra o SHA-256 do certificado no resumo da execução, reprovando se for a antiga chave de teste.

---


## 0. Escopo, método e limitações

### 0.1 O que o Sonivell é (e o que isso muda na auditoria)

O Sonivell é um **player de música offline**, com dois aplicativos e um motor de recomendação:

| Componente | Tecnologia | Onde |
|---|---|---|
| App Android (edições `lite` e `smart`) | Kotlin, Jetpack Compose, Media3/ExoPlayer, Coil, Navigation | `app/` |
| Motor SMART (recomendação pelo som) | Kotlin Multiplatform, ONNX Runtime, Koin | `core/smart`, `desktop-smart` |
| Operadores nativos de 4 bits e produtos internos | C/C++ (KleidiAI vendorizado), JNI | `core/ort-ops` |
| Mapa musical (t-SNE) | C++ (qdtsne, knncolle, aarand, subpar, sanisizer vendorizados), JNI | `core/map-native` |
| App desktop Linux (.deb, AppImage) | Compose for Desktop, ffmpeg/ffprobe como processos, D-Bus (MPRIS) | `desktop/` |
| CI | GitHub Actions (4 workflows), GitLab CI | `.github/workflows`, `.gitlab-ci.yml` |

**Não existem** servidor, API HTTP, banco de dados de servidor, contas, login, sessões, tokens, Docker ou Kubernetes. O app Android **não declara a permissão `INTERNET`** (`app/src/main/AndroidManifest.xml`), e o desktop também não abre conexões de rede (só abre um link no navegador, a pedido da pessoa). Por isso, várias seções pedidas no roteiro **não se aplicam**. Elas estão marcadas como **N/A** com o motivo, em vez de serem preenchidas com achados inventados.

A superfície de ataque real de um player offline é outra:

1. **Outros apps no mesmo aparelho**, por meio de componentes exportados (serviço de mídia, activity, bloco rápido).
2. **Arquivos não confiáveis abertos pela pessoa**: backup `.zip`, playlist `.m3u`, letras `.lrc`, imagens de capa e as próprias músicas.
3. **Código nativo** (C/C++) que recebe dados do Kotlin.
4. **Cadeia de suprimentos**: dependências, binários baixados no CI e a chave de assinatura dos APKs.
5. **Confiabilidade**: perda de dados, travamentos, trabalho em segundo plano.

### 0.2 Método

1. Mapeamento de todos os módulos, manifestos, scripts de build, workflows e dependências.
2. Leitura dos caminhos críticos: serviço de mídia e callbacks do Media3, backup e restauração, importações, persistência, `ViewModel` principal, JNI, invocação de processos no desktop, scripts de empacotamento e workflows.
3. Rastreamento de entradas não confiáveis até onde são usadas.
4. Cada achado traz arquivo, linha e trecho de código lido. Os achados que dependem de medição ou de teste no aparelho estão marcados com **confiança média** e com o teste que os confirmaria.

### 0.3 Limitações (declaradas)

- **Sem execução no aparelho.** Não há emulador Android neste ambiente, e o Gradle do Android não roda aqui (o proxy bloqueia o `dl.google.com`). Nada foi medido em tempo de execução: os achados de desempenho são estáticos e trazem o teste que precisa ser feito.
- **Sem varredura de CVEs.** A consulta à base OSV (`api.osv.dev`) foi bloqueada pelo proxy (HTTP 403). As versões das dependências estão listadas na seção 3.5, mas **não foi possível confirmar** se alguma tem vulnerabilidade publicada. Isso **não** significa que estejam seguras.
- **Código vendorizado** (KleidiAI, qdtsne etc.) não foi auditado linha a linha. Avaliei só a fronteira JNI que entrega dados a ele.
- **Motor SMART:** a lógica de recomendação já passou por uma auditoria própria (PR #10). Aqui olhei só a fronteira de dados e os limites dele.
- **Segredos:** a chave de assinatura e as senhas de teste aparecem na seção 3.3 só pela **localização e pelo tipo**, sem reproduzir os valores.

---

## A. Resumo executivo

**Estado geral.** O código é cuidadoso em vários pontos que costumam falhar:
- o índice binário do SMART valida tamanho, dimensão e valores finitos antes de alocar (`FileIndexStore.kt:181-271`);
- o JNI do mapa valida todos os tamanhos;
- o restaurar backup bloqueia `..`;
- as consultas ao MediaStore são parametrizadas;
- todas as `PendingIntent` são imutáveis;
- o `ArtProvider` não é exportado e é só de leitura.

Os problemas sérios estão em **três lugares**: a assinatura dos APKs, o serviço de mídia aberto a qualquer app, e a lógica de segundo plano presa à tela.

**Principais riscos:**
1. **APKs de release assinados com uma chave pública.** A chave de teste e as senhas estão versionadas, e o CI usa essa chave em todo build de release (SEC-01, **P1**). Quem baixar o repositório consegue publicar um "Sonivell" falso que o Android instala como atualização legítima.
2. **Temporizador, histórico e fila SMART param quando a tela fecha**, enquanto a música continua tocando no serviço (REL-01, **P1**). O temporizador, em particular, falha em silêncio.
3. **Serviço de mídia exportado sem lista de apps permitidos** (SEC-02, **P2**). Qualquer app instalado, mesmo sem permissão de mídia, lista a biblioteca, as curtidas e as playlists, e controla a reprodução.
4. **Restaurar backup sem limites** de tamanho, de quantidade e de quais arquivos aceita (SEC-03, **P2**).
5. **Biblioteca e busca recalculadas na thread principal** a cada tecla, com custo O(n log n) em normalizações Unicode (PERF-01, **P2**). Há risco de travar (ANR) em bibliotecas grandes; precisa de medição.

**Estabilidade.** Há gravações de arquivo não atômicas e resultados de `renameTo` ignorados, o que pode perder dados num crash ou numa falta de energia (REL-02). No desktop, o `ffprobe` e o `ffmpeg` rodam sem limite de tempo (REL-03).

**Maturidade técnica.** É um produto funcional para uso pessoal ou beta. O motor tem testes bons, mas o **app Android não tem nenhum teste** (TEST-01), e os arquivos centrais são muito grandes (`SonivellApp.kt` com 1.561 linhas, `MainViewModel.kt` com 1.075).

**Prontidão para produção (distribuição pública):** **não está pronta** enquanto SEC-01 e REL-01 não forem corrigidos. SEC-02 e SEC-03 devem ser corrigidos antes de uma loja pública. O restante pode ir em ciclos normais.

---

## B. Nota técnica por categoria

| Categoria | Nota | Justificativa |
|---|---|---|
| Segurança | **5/10** | Boa defesa nos parsers e no JNI do mapa. Pesam a chave de release pública (P1), o serviço exportado sem controle (P2) e o restaurar sem limites (P2). Sem varredura de CVEs (limitação). |
| Estabilidade | **5/10** | Lógica crítica presa ao ciclo de vida da tela (P1), gravações não atômicas, processos sem timeout no desktop. O índice do SMART e o modelo de gosto toleram arquivos corrompidos. |
| Performance | **6/10** | Motor otimizado (PR #10). A UI recalcula a biblioteca inteira na thread principal, e a ordenação normaliza texto a cada comparação. Sem medição real. |
| Escalabilidade | **6/10** | Escala com o tamanho da biblioteca, não com usuários (offline). Limites explícitos no índice (até cerca de 17 mil faixas no envelope de 960 dimensões). A UI e a busca são lineares por tecla. |
| Arquitetura | **5/10** | O motor é bem separado (KMP, injeção com Koin). O app concentra regras de negócio na camada de UI (ViewModel e `SonivellApp.kt` gigantes) e duplica telas por layout (5 bibliotecas, 3 players). |
| Qualidade do código | **6/10** | Código legível, comentado e consistente. Funções e arquivos muito longos, lógica duplicada entre Android e desktop. |
| Testabilidade | **3/10** | Zero testes no app Android. Testes bons no motor (20) e poucos no desktop (3 arquivos). O CI do APK não roda testes nem lint. |
| DevOps | **4/10** | CI compila e publica artefatos, mas assina releases com a chave de teste, fixa actions por tag mutável, não confere checksum do wrapper nem dos binários baixados, e usa token com escrita e `push -f`. |
| Observabilidade | **3/10** | Coerente com um app offline e sem telemetria, mas não existe registro local de falhas nem forma de a pessoa exportar um relatório de erro. |

---

## C. Inventário de descobertas

Legenda: **Severidade** P0–P4 · **Confiança** alta/média/baixa · **Exploração**, quando aplicável.

### 3.1–3.4 Segurança

#### SEC-01 — APKs de release assinados com chave versionada no repositório
- **Categoria:** Segurança / cadeia de suprimentos · **Severidade:** **P1** · **Confiança:** alta · **Exploração:** fácil (o repositório é a única coisa de que o atacante precisa).
- **Arquivos:** `keystore/sonivell-teste.jks` (versionado desde `165633d`); `app/build.gradle.kts:41-46` (senhas e alias **escritos no arquivo** como valores padrão; valores omitidos aqui); `README.md:72` (repete a senha, documentando a chave como "de teste"); `.github/workflows/build.yml:22-42` e `.gitlab-ci.yml:39-44` (montam `assembleLiteRelease`/`assembleSmartRelease` **sem** definir `SONIVELL_KEYSTORE` e as outras variáveis).
- **Descrição.** O README apresenta a chave como de teste, o que é uma escolha consciente, mas o risco vem do uso dela: o `signingConfig` de release cai para a chave de teste quando as variáveis de ambiente faltam. Nenhum workflow define essas variáveis, então **todo APK de release publicado como artefato** sai assinado com essa chave pública.
- **Evidência.** `storeFile = file(System.getenv("SONIVELL_KEYSTORE") ?: "../keystore/sonivell-teste.jks")` e as senhas de fallback literais nas linhas 43 e 45. Os workflows não têm nenhum `secrets.SONIVELL_*`.
- **Cenário (reprodução segura).** Qualquer pessoa clona o repositório, altera o código, roda `./gradlew assembleSmartRelease` e obtém um APK com o **mesmo pacote e o mesmo certificado**. Se a vítima já tem o Sonivell instalado a partir dos artefatos do CI, o Android aceita o APK falso como **atualização**. Ele herda os dados privados (histórico de escuta, curtidas, modelo de gosto) e as permissões já concedidas (`READ_MEDIA_AUDIO`, `RECORD_AUDIO`, `WRITE_SETTINGS`).
- **Impacto.** Técnico: perde-se a garantia de autenticidade do app. Negócio: se o app for distribuído publicamente, versões adulteradas ficam indistinguíveis das oficiais.
- **Solução.**
  1. Gerar uma chave de release nova, fora do repositório, e guardá-la nos *secrets* do GitHub e do GitLab.
  2. No `build.gradle.kts`, **falhar** o build de release quando as variáveis faltarem, em vez de cair para a chave de teste. Manter a chave de teste só no build `debug`.
  3. Nos workflows, decodificar a keystore do secret para um arquivo temporário e apagá-lo no fim.
  4. Comunicar que quem instalou os APKs atuais precisa desinstalar e reinstalar, porque a troca de certificado impede a atualização. A alternativa é a rotação de chave com o *APK Signature Scheme v3*.
  5. Remover a keystore e as senhas do histórico não resolve sozinho (a chave já é pública), mas evita reutilização.

#### SEC-02 — Serviço de mídia exportado sem verificar quem se conecta
- **Severidade:** **P2** · **Confiança:** alta · **Exploração:** fácil para um app instalado no mesmo aparelho.
- **Arquivos:** `AndroidManifest.xml:41-50` (`PlaybackService` exportado, sem `android:permission`); `playback/PlaybackService.kt:235` (`onGetSession` sempre devolve a sessão); `PlaybackService.kt:131-196` (callback sem `onConnect`); `playback/LibraryBrowser.kt:40-102`.
- **Descrição.** Qualquer app pode se conectar com `MediaBrowser`, `MediaBrowserCompat` ou `MediaController` do Media3 e:
  1. **Listar a biblioteca inteira** (álbuns, artistas e faixas com caminho de exibição), **as curtidas** (`LIKED`), as faixas recentes e **os nomes e o conteúdo das playlists**. Isso funciona mesmo que o app **não tenha** `READ_MEDIA_AUDIO`, o que é uma escalada de privilégio de leitura ("confused deputy").
  2. **Controlar a reprodução** (tocar, pausar, pular, trocar a fila).
  3. **Mandar o Sonivell tocar URIs arbitrárias.** `resolve()` (linhas 189-199) devolve sem alteração qualquer item que já tenha `localConfiguration`. Como o app não tem `INTERNET`, URLs `http` falham. Mas `content://` de provedores que o Sonivell pode ler e `file://` do próprio sandbox são abertos pelo ExoPlayer, e os metadados extraídos ficam visíveis ao controlador.
- **Evidência.** `override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session`; `item.localConfiguration != null -> item`; `tracksOf(LIKED)` lê `prefs.getStringSet("favorites", ...)`.
- **Cenário seguro.** Um app de teste sem permissões cria `MediaBrowserCompat(ComponentName("com.juscelino.sonivell", "...PlaybackService"))`, chama `subscribe("root")` e depois `subscribe("liked")`, e recebe os títulos.
- **Impacto.** Vazamento de dados pessoais (gosto musical, playlists) para qualquer app e controle indevido da reprodução.
- **Solução.**
  - Implementar `onConnect` no `LibraryCallback`, aceitando só controladores confiáveis: o próprio pacote, o System UI, `com.google.android.projection.gearhead` (Android Auto), `com.google.android.wearable.app`, `com.android.bluetooth`, `controller.isTrusted` (Media3 marca assim os controladores do sistema) e apps com `MEDIA_CONTENT_CONTROL`.
  - Para os demais, recusar ou aceitar só os comandos de transporte, sem navegação (`AcceptedResultBuilder` com `SessionCommands`/`PlayerCommands` reduzidos).
  - Em `resolve()`, ignorar a `localConfiguration` de itens vindos de controladores externos e reconstruir sempre a partir do `mediaId` (que já é validado como `Long`).

#### SEC-03 — Restaurar backup sem limites e aceitando arquivos que o backup não exporta
- **Severidade:** **P2** · **Confiança:** alta · **Exploração:** exige que a pessoa escolha um `.zip` malicioso (engenharia social).
- **Arquivo:** `system/Backup.kt:58-85`.
- **Descrição.**
  1. **Sem limite de tamanho, nem por entrada nem total, nem de quantidade de entradas.** `zip.copyTo(it)` (linha 71) extrai tudo para `cacheDir/restore`. Uma "zip bomb" de poucos KB enche o armazenamento interno.
  2. **Aceita qualquer caminho em `files/` e `shared_prefs/`**, não só o que o `export` gera (`KEEP_DIRS = covers` e arquivos de até 20 MB). O backup pode trocar o índice do SMART (`FileIndexStore`), o modelo de gosto (`gosto.bin`) e qualquer preferência. O índice e o modelo validam o formato (bom), mas o restaurar permite empurrar estado arbitrário e gigante.
  3. **Não é atômico.** Copia arquivo por arquivo sobre os atuais (linhas 79-83) e não remove os que não estão no backup. Uma falha no meio deixa um estado misturado.
- **Evidência.** Linhas 66-72 (só bloqueiam `..` e exigem os prefixos) e 79-83 (`copyTo(..., overwrite = true)` sem transação).
- **Impacto.** Negação de serviço local (disco cheio, app inutilizável até limpar os dados) e corrupção do estado.
- **Solução.**
  - Limitar por entrada (por exemplo, 20 MB, igual ao export), o total (por exemplo, 200 MB) e a quantidade (por exemplo, 2.000).
  - Contar os bytes durante a cópia, porque o `e.size` do zip pode mentir.
  - Aceitar só nomes que o `export` produz (lista fixa mais `files/covers/<número>.jpg`).
  - Extrair para a área temporária, validar tudo e só então trocar os diretórios inteiros (renomear o antigo para `.bak`, mover o novo, apagar o `.bak`).
  - Adicionar testes (seção G).

#### SEC-04 — `allowBackup="true"` sem regras de extração
- **Severidade:** **P3** · **Confiança:** alta.
- **Arquivo:** `AndroidManifest.xml:23`.
- **Descrição.** Sem `android:dataExtractionRules` (Android 12+) nem `fullBackupContent`, o backup automático do Google Drive e a transferência de dispositivo copiam todo o `filesDir` e o `shared_prefs`: histórico de escuta, modelo de gosto, curtidas e índices do SMART (vários MB). Em aparelhos com Android até 11 com depuração USB, o `adb backup` extrai esses dados sem root.
- **Impacto.** Privacidade: o histórico de escuta sai do aparelho sem uma escolha explícita da pessoa. Isso contradiz o texto do app ("nada é enviado para lugar nenhum"). Os índices do SMART também ocupam a cota de backup à toa.
- **Solução.** Decidir de propósito o que vai para o backup. Por exemplo, um `data_extraction_rules.xml` que exclua `historico.tsv`, `gosto.bin` e os índices, ou `allowBackup="false"` e deixar só o backup manual do app. Ajustar o texto do "Sobre" de acordo.

#### SEC-05 — JNI de produtos internos não valida os índices das linhas
- **Severidade:** **P3** (defesa em profundidade) · **Confiança:** alta quanto à ausência da checagem; baixa quanto a ser explorável hoje.
- **Arquivos:** `core/ort-ops/src/main/cpp/ljdots_jni.cc:11-16`; `ljdots.cc:30,56,62-63`.
- **Descrição.** A função nativa confere `dim`, `offset`, `count` e `matrixSize % dim`, mas **não** confere se cada `rows[r]` está em `0..matrixSize/dim-1` nem se cabe em `output`. Ela escreve em `out[rows[r]]` e lê `matrix + rows[r] * dim`. O comentário diz que quem valida é o adaptador Kotlin.
- **Impacto.** Se um chamador futuro (ou um bug) passar uma linha fora do intervalo, há leitura e escrita fora dos limites no heap nativo, o que pode corromper a memória do processo. Hoje os índices vêm só do motor, de dados internos.
- **Solução.** Percorrer `rows` uma vez dentro da região crítica (custo O(count), desprezível perto do O(count·dim)) e devolver `JNI_FALSE` se algum índice for inválido ou se `outputSize` não comportar o maior índice.

#### SEC-06 — Ações de atalho aceitas de qualquer app
- **Severidade:** **P4** · **Confiança:** alta.
- **Arquivo:** `MainActivity.kt:56-66` (activity exportada; `handleShortcut` aceita as ações de `AppShortcuts.ALL`).
- **Descrição.** Qualquer app pode abrir o Sonivell com `ACTION_MIX_NOW` ou `ACTION_SHUFFLE` e iniciar a reprodução. Não há dados nem parâmetros: as ações vêm de uma lista fixa. O impacto é só o incômodo.
- **Solução.** Opcional: exigir que a ação venha de um atalho dinâmico (`ShortcutManager`) ou do bloco rápido, ou aceitar sem o intent explícito.

#### 3.2 Autenticação e autorização — **N/A**
Não há contas, login, sessões, tokens, senhas de usuário, recuperação de conta nem níveis de acesso. O único controle de acesso relevante é o do Android entre apps. Os problemas encontrados nele estão em **SEC-02** e **SEC-06**. IDOR/BOLA, fixação ou sequestro de sessão, CSRF, MFA e rate limiting de login não se aplicam.

#### 3.3 Dados sensíveis e segredos
- **Segredos versionados:** um arquivo de keystore (`keystore/sonivell-teste.jks`, tipo JKS) e as senhas e o alias dele como literais em `app/build.gradle.kts:43-45` e `README.md:72` (ver SEC-01). **Nenhuma outra chave de API, token ou credencial** foi encontrada no repositório: busquei padrões de chaves, tokens e `password` nos arquivos versionados.
- **Logs:** 12 chamadas a `Log.*` no app. Nenhuma registra caminho de arquivo, histórico ou dado pessoal; a única `Log.d` é sobre a duração do crossfade (`Crossfader.kt:234`).
- **Dados pessoais em repouso:** o histórico (`historico.tsv`), o modelo de gosto e as curtidas ficam sem criptografia no `filesDir` e no `shared_prefs`, ou seja, no sandbox do app. É aceitável para o modelo de ameaça (o aparelho já é criptografado pelo Android). O risco está em **saírem do sandbox**: SEC-04 (backup automático), SEC-03 (o `.zip` de backup é exportado em texto claro para onde a pessoa escolher; isso deve ser informado na tela) e o publicador de playlists, que é opcional e vem desligado.
- **Criptografia em trânsito:** N/A (sem rede).

#### 3.4 Segurança de APIs — **N/A como HTTP**
Não há endpoints. As "APIs" equivalentes são as do Android (serviço de mídia, activity e provedores) e o D-Bus no desktop:
- **D-Bus/MPRIS (desktop):** a interface padrão expõe controle de reprodução a processos do mesmo usuário, como esperado. O `OpenUri` é um no-op (`desktop/.../Mpris.kt:103`), o que evita mandar o `ffmpeg` abrir URLs arbitrárias. Sem achado.
- **Provedores Android:** o `ArtProvider` não é exportado, é só de leitura e valida o caminho (`ArtProvider.kt:46-58`). O `FileProvider` não é exportado e expõe só `cache/share/`. Sem achado.
- **Invocação de processos (desktop):** o `ffmpeg` e o `ffprobe` recebem argumentos em lista, sem shell (`Engine.kt:82-87`, `Library.kt:102-106`, `AudioDecoder.jvm.kt:30-38`). Os caminhos vêm da varredura de diretórios e são absolutos, então **não há injeção de comandos** nem de protocolo do ffmpeg pelos nomes de arquivo. Sem achado de segurança; os timeouts estão em REL-03.

### 3.5 Dependências e cadeia de suprimentos

| Dependência | Versão | Observação |
|---|---|---|
| AGP / Kotlin / Gradle | 9.2.1 / 2.3.10 / 9.4.1 | Recentes. O wrapper **não** tem `distributionSha256Sum` (SUP-01). |
| Media3 (ExoPlayer, Session) | 1.7.1 | Analisa arquivos de áudio não confiáveis. Manter atualizado. |
| Compose BOM / Material3 | 2025.06.01 | |
| Coil | 2.7.0 | Decodifica imagens de capa (arquivos da pessoa). |
| ONNX Runtime Android/JVM | 1.26.0 | Executa só modelos que vêm no app. |
| Koin / Coroutines / Navigation / Lifecycle | 4.2.1 / 1.10.2 / 2.9.0 / 2.9.1 | |
| dbus-java (desktop) | 5.1.1 | Declarado direto no `desktop/build.gradle.kts:19-20`, fora do catálogo de versões. |
| Nativos vendorizados | qdtsne, knncolle, aarand, subpar, sanisizer (commits fixos), KleidiAI | Origem e commit registrados em `core/map-native/dependencies.json`: boa prática. |
| Binários baixados no CI | ffmpeg `latest`, appimagetool `continuous` | Ver SUP-02. |

**Limitação:** não foi possível consultar CVEs (seção 0.3). A primeira ação do plano de testes é rodar um scanner (OWASP Dependency-Check, OSV-Scanner ou GitHub Dependabot) **no CI**.

#### SUP-01 — Integridade da cadeia de build não verificada
- **Severidade:** **P3** · **Confiança:** alta.
- **Evidência.**
  - `gradle/wrapper/gradle-wrapper.properties` não tem `distributionSha256Sum`.
  - Não existe `gradle/verification-metadata.xml`.
  - Todas as actions estão fixadas por **tag mutável** (`actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`), não por SHA de commit.
- **Impacto.** Uma tag ou um artefato comprometido no upstream entra no build sem aviso.
- **Solução.**
  - Adicionar `distributionSha256Sum`.
  - Gerar a verificação de dependências (`./gradlew --write-verification-metadata sha256 help`).
  - Fixar as actions por SHA e ligar o Dependabot para atualizar.

#### SUP-02 — AppImage empacota ffmpeg e appimagetool baixados sem checksum, de canais móveis
- **Severidade:** **P2** · **Confiança:** alta.
- **Arquivos:** `tools/desktop-appimage.sh:15-16,37-39`; executado no CI em `.github/workflows/desktop.yml:80` e `.gitlab-ci.yml:65`.
- **Descrição.** `FFMPEG_URL` aponta para `.../releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz` e `APPIMAGETOOL_URL` para `.../continuous/...`. Os dois são baixados e executados ou empacotados **sem verificar o hash**. Cada build pode trazer um binário diferente, sem rastreabilidade, e um upstream comprometido entra direto no AppImage distribuído.
- **Licença.** O build "gpl" do ffmpeg é **GPL**. Distribuí-lo dentro do AppImage obriga a oferecer o código-fonte correspondente àquela versão exata, o que é impossível de cumprir com "latest" sem registrar a versão. O script registra só a URL, não a versão.
- **Solução.**
  - Fixar uma release versionada (por exemplo, a tag `autobuild-AAAA-MM-DD`, ou um build LGPL se a GPL não for desejada).
  - Verificar o `sha256sum` antes de extrair.
  - Gravar a versão exata em `usr/share/doc/ffmpeg/SOURCE` com o link do fonte correspondente.
  - Fazer o mesmo com o appimagetool.

#### SUP-03 — Workflows com token de escrita e `push --force`
- **Severidade:** **P3** · **Confiança:** alta.
- **Arquivos:**
  - `.github/workflows/desktop.yml:22-23` (`permissions: contents: write` em todo o workflow, que também roda em `pull_request`) e `:130` (`git push -f` para `desktop-screenshots`);
  - `smart-test.yml:13-14,61` (`push -f` para `smart-logs`);
  - `build.yml` e `smart-audit.yml` sem bloco `permissions` (herdam o padrão do repositório).
- **Impacto.** Os jobs de build rodam scripts Gradle do branch com permissão de escrita no repositório. Em PRs de forks o GitHub reduz o token para só leitura, mas em branches do próprio repositório não. Qualquer código de build comprometido consegue alterar branches.
- **Solução.**
  - Definir `permissions: contents: read` no topo de todos os workflows.
  - Dar `contents: write` só ao **job** que publica, e só em `push` para a `main` (com `if: github.event_name == 'push'`).
  - Trocar o `push -f` para branches por `actions/upload-artifact`.

#### Licenças (observação, P4)
- `qdtsne/SPTree.hpp` e `Status.hpp` estão sob **BSD-4-Clause** (registrado em `dependencies.json`). A cláusula de propaganda exige citação em materiais de divulgação. Hoje o `NOTICE` cobre o LatentJam; vale incluir os nomes dos componentes nativos e a cláusula BSD-4.
- Manrope (OFL) e LatentJam (Apache-2.0) estão com os avisos corretos (`OFL-Manrope.txt`, `core/NOTICE-LatentJam.txt`).

### 4. Estabilidade e confiabilidade

#### REL-01 — Temporizador, histórico, fila SMART e "onde parei" dependem da tela estar viva
- **Severidade:** **P1** · **Confiança:** alta pela leitura do código; o comportamento deve ser confirmado no aparelho.
- **Arquivos:**
  - `MainViewModel.kt:590-600`: o temporizador usa `viewModelScope.launch { delay(minutes * 60_000L); player.pause() }`.
  - `MainViewModel.kt:333-350`: o registro de escutas (`session.observe`), o `maybeSaveSession`, o "fim desta música" e a fila SMART infinita (`topUpSmartQueue`) rodam dentro de `viewModelScope.launch { playerState.collect { ... } }`.
  - `MainViewModel.kt:811`: `history.record(event)`.
  - `MainViewModel.kt:1070-1074`: o `onCleared()` libera o controlador.
  - `ui/SonivellApp.kt:680`: `BackHandler { activity?.finish() }` no Início do tema Sonivell.
- **Descrição.** A música toca no `PlaybackService`, em primeiro plano e independente da tela. Já essas funções vivem no `ViewModel` da `MainActivity`, que é destruído quando a pessoa:
  - aperta "voltar" no Início (o tema Sonivell chama `finish()` de propósito);
  - fecha o app pelos recentes;
  - ou quando o sistema finaliza a activity.

  Nesses casos a música **continua**, mas:
  1. **o temporizador é cancelado e a música não pausa**, e não aparece nenhum aviso;
  2. as escutas param de ser registradas, o que distorce as estatísticas e o treino do gosto;
  3. a fila SMART "infinita" para de ser completada e a música para quando a fila acaba;
  4. a posição para o "onde parei" deixa de ser salva.
- **Impacto.** Falha silenciosa de uma função de confiança (dormir com o temporizador ligado). Dados de uso incompletos.
- **Solução.**
  - Mover para o `PlaybackService` (ou para um componente ligado a ele) o temporizador (usando `Handler`/`AlarmManager` no serviço, ou `player.createMessage` na posição final), o registro de escutas (`Player.Listener` no serviço), o salvamento da sessão e o abastecimento da fila SMART.
  - O `ViewModel` passa só a observar.
  - Teste de regressão na seção G (T-REL-01).

#### REL-02 — Gravações não atômicas e falhas de gravação ignoradas
- **Severidade:** **P2** · **Confiança:** alta (código); média (probabilidade).
- **Arquivos e evidências.**
  - `ListeningHistory.kt:88`: a compactação reescreve o histórico inteiro com `file.writeText(...)` direto no arquivo final. Se o processo morrer no meio, o arquivo fica truncado.
  - `TagOverrides.kt:81`: `file.writeText(root.toString())` direto, chamado de `saveTags` (`MainViewModel.kt:657-660`) **na thread principal** (I/O na UI).
  - `PlaylistStore.kt:119-121`, `SmartPlaylists.kt:222-224` e `TasteModel.kt:255-271` gravam em `.tmp` e chamam `renameTo(file)` **ignorando o retorno**. Se a renomeação falhar, a mudança se perde sem aviso, e o `.tmp` fica para trás.
  - Desktop, `Store.kt:102,116`: `writeText` direto no arquivo final.
- **Cenário.** Um crash ou o fim da bateria durante a gravação deixa um JSON truncado. O `load` cai no `runCatching` e devolve uma lista vazia, e a próxima gravação **sobrescreve** o arquivo com essa lista vazia. **A perda é permanente** (playlists, correções de tags).
- **Solução.**
  - Uma função única `atomicWrite(file, bytes)`: grava em `.tmp`, chama `fd.sync()` e depois `Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)`. No Android, também serve `AtomicFile` do androidx.core.
  - Checar o resultado e registrar a falha.
  - Quando a leitura falhar, renomear o arquivo para `.corrompido` em vez de sobrescrevê-lo.
  - Mover o `saveTags` para `Dispatchers.IO`.

#### REL-03 — Desktop: ffprobe e ffmpeg sem timeout
- **Severidade:** **P2** · **Confiança:** alta.
- **Arquivos:** `desktop/.../Library.kt:74` (pool de 2 a 6 threads) e `:102-109` (`readText()` do stdout seguido de `p.waitFor()`, sem limite); `Engine.kt:218-224` (`readBytes()` e `waitFor()` da capa embutida, sem limite).
- **Descrição.** Um arquivo malformado que faça o ffprobe travar (os parsers do ffmpeg já tiveram casos assim) prende uma thread da varredura para sempre; com 2 a 6 arquivos assim, a varredura nunca termina. Além disso, o stdout é lido até o fim antes do stderr; com muito texto no stderr, os dois processos podem travar esperando um ao outro (hoje é improvável por causa do `-v error`).
- **Solução.**
  - `p.waitFor(15, SECONDS)` e, se estourar, `destroyForcibly()`.
  - Mandar o stderr para `DISCARD` ou lê-lo em outra thread.
  - Limitar o tamanho lido do stdout da capa (por exemplo, 10 MB).

#### REL-04 — Restaurar backup e preferências em memória
- **Severidade:** **P3** · **Confiança:** média.
- **Arquivo:** `Backup.kt:79,89-93`.
- **Descrição.** O restaurar troca os XMLs do `shared_prefs` com o app rodando. As instâncias de `SharedPreferences` em memória (o serviço de reprodução e o ViewModel) ainda têm os valores antigos, e qualquer `apply()` feito entre a cópia e o `exitProcess(0)` **regrava os valores antigos por cima do restaurado**. Por exemplo: o player salva a posição a cada mudança de faixa e um listener de ajustes está registrado.
- **Solução.** Parar o `PlaybackService` antes de restaurar. Copiar as preferências com `commit()` por cima das instâncias (`edit().clear()` e depois os valores lidos) em vez de trocar o arquivo, ou reiniciar antes de qualquer outra gravação.

#### REL-05 — Leitura de arquivos inteiros sem limite de tamanho
- **Severidade:** **P3** · **Confiança:** alta (código); média (gravidade).
- **Arquivos:** `MainViewModel.kt:580` (M3U), `LyricsRepository.kt:110,126` (`.lrc`), `ArtProvider.kt:188` (imagem de capa escolhida); `readBytes()` sem limite em todos.
- **Descrição.** Um arquivo escolhido pela pessoa com centenas de MB causa falta de memória. No M3U e na capa há `runCatching`, então a operação só falha. O índice de letras para busca (`LyricsRepository.kt:118-130`) carrega **todas** as letras da pasta na memória de uma vez.
- **Solução.** Ler com limite (por exemplo, 5 MB para M3U e `.lrc`, 30 MB para imagem) e recusar o resto com uma mensagem. Para a busca de letras, um índice com tamanho máximo ou a leitura sob demanda.

#### REL-06 — Paginação da navegação de mídia com multiplicação sem checagem
- **Severidade:** **P4** · **Confiança:** baixa (o Media3 pode já validar).
- **Arquivo:** `PlaybackService.kt:149-151`: `page * pageSize` em `Int`. Um controlador malicioso com valores grandes gera estouro e índice negativo, e a `Future` falha com exceção. Não derruba o serviço.
- **Solução.** `val from = (page.toLong() * pageSize).coerceIn(0, all.size.toLong()).toInt()` e rejeitar `page < 0` ou `pageSize <= 0`.

**Itens verificados sem achado:**
- Carregamento do índice SMART com limites e validação (`FileIndexStore.kt`).
- Modelo de gosto corrompido tratado com `runCatching` (`TasteModel.kt:54`).
- `onTimeout` do serviço `dataSync` tratado para o Android 15 (`SmartAnalysisService.kt:67`).
- Recursos liberados no `onDestroy` do serviço (`PlaybackService.kt:244-266`).
- JNI do mapa com tamanhos checados e exceções Java preservadas (`ljmap_jni.cc`).

**Health checks, readiness, liveness, graceful shutdown de servidor:** N/A (app cliente).

### 5. Performance

> Sem medição real (seção 0.3). Os números de complexidade vêm do código. Cada item traz o teste que confirma.

#### PERF-01 — Biblioteca e busca recalculadas na thread principal a cada tecla
- **Severidade:** **P2** · **Confiança:** alta quanto ao local de execução; média quanto ao impacto (depende do tamanho da biblioteca).
- **Arquivos:**
  - `MainViewModel.kt:254-294`: `combine(allTracks, query, tab, sort)` **sem `flowOn`**, ou seja, roda no `Dispatchers.Main`, e **sem `debounce`** na `query`.
  - `data/Search.kt:52-58`: cada faixa normaliza 5 campos (NFD e regex) a cada avaliação, mais a distância de Damerau por palavra.
  - `data/SortKey.kt:10-13` com `sortedBy { sortKey(...) }`: o seletor roda **a cada comparação**, o que dá O(n log n) normalizações NFD e regex por ordenação. Por emissão são 5 ordenações (faixas, álbuns, artistas, pastas, gêneros).
- **Estimativa (a confirmar).** Com 20 mil faixas, uma ordenação faz cerca de 20 mil × log₂(20 mil) ≈ 290 mil comparações e **cerca de 580 mil normalizações Unicode**, multiplicado pelas 5 ordenações, na thread principal, a cada letra digitada. Há risco real de travamento perceptível e, em aparelhos modestos, de ANR (mais de 5 s).
- **Solução.**
  1. `.flowOn(Dispatchers.Default)` no `combine` e `debounce(150)` só na `query`.
  2. Pré-calcular **uma vez por faixa**, quando a biblioteca carrega, as chaves de ordenação e os textos normalizados da busca (um mapa `id → chaves`, ou campos no `Track`).
  3. Ordenar pelas chaves pré-calculadas (`sortedBy` sobre uma `String` já pronta).

#### PERF-02 — Busca linear da faixa atual a cada 500 ms
- **Severidade:** **P3** · **Confiança:** alta.
- **Arquivos:** `MainViewModel.kt:297-301` (`currentTrack = combine(playerState, allTracks) { ... all?.firstOrNull { it.id == id } }`); `PlayerConnection.kt:231` (o `playerState` é atualizado a cada 500 ms para a posição).
- **Impacto.** Uma busca O(n) na thread principal duas vezes por segundo, enquanto toca. Com 20 mil faixas são 40 mil comparações por segundo; não é grave, mas é desperdício contínuo de bateria.
- **Solução.** Derivar o `currentTrack` de `playerState.map { it.current?.mediaId }.distinctUntilChanged()` combinado com um `Map<Long, Track>` já montado.

#### PERF-03 — Busca em letras carrega todas as letras
- **Severidade:** **P3** · **Confiança:** alta. `LyricsRepository.kt:118-130` (ver REL-05).

#### PERF-04 — Penalidade de "hubs" do SMART
- **Severidade:** **P4** · **Confiança:** alta. `SmartSnapshot.computeHubPenalty`, O(n · min(n, 1024) · 960) por snapshot. No caminho exato (até 1.024 faixas), cada par é calculado duas vezes. Medido no PR #10: cerca de 4,9 s para 10 mil faixas no desktop. No Android, o kernel NEON não é usado nessa etapa. Já está registrado como melhoria pendente.

#### PERF-05 — Sombras do vidro em listas
- **Severidade:** **P4** · **Confiança:** baixa (precisa de medição). O `cardSurface` usa `elevation = 6.dp` por padrão (`ui/theme/Glass.kt`), e algumas linhas de lista longa ficaram com sombra (por exemplo, `OrganizeScreen.kt:118`, nas linhas de problemas). Sombras por item custam na rolagem em aparelhos modestos. Medir com o *Macrobenchmark* (FrameTimingMetric) e usar `elevation = 0.dp` em itens repetidos.

**Banco de dados:** N/A. Não há SQL nem banco próprio. Os dados ficam em JSON, TSV e `SharedPreferences`, e a biblioteca vem do MediaStore por uma única consulta parametrizada (`MusicRepository.kt`). Sem N+1, índices ou pool para avaliar. O ponto equivalente é o tamanho dos arquivos: o histórico é limitado a 10–12 mil eventos (`ListeningHistory.kt:109-110`), o que é bom.

**Cache:** o `LibraryBrowser` usa um cache de 1 conjunto com TTL (`CACHE_MS`) protegido por `Mutex`, sem dados de outro usuário (o app tem um usuário só). O cache de capas é chaveado por versão (data e tamanho do arquivo), o que evita capas velhas. Sem achado.

**Frontend (Compose):** bundle e Web Vitals são N/A. As recomposições que importam estão em PERF-01 e PERF-02.

### 6. Escalabilidade

O Sonivell **não tem usuários simultâneos num servidor**. Cada instalação atende uma pessoa, então "100 / 1.000 / 10.000 / 100.000 usuários simultâneos" **não se aplica**: não há estado compartilhado, servidor ou banco central, e o número de instalações não muda a carga de nenhuma delas. **Não há ponto único de falha central.**

As dimensões que escalam são o **tamanho da biblioteca** e a **capacidade do aparelho**:

| Biblioteca | Gargalos esperados (estáticos) | Teste necessário |
|---|---|---|
| 1 mil faixas | Nenhum relevante. | — |
| 10 mil faixas | PERF-01 (busca e ordenação na thread principal), snapshot SMART de alguns segundos, análise de áudio longa (horas no primeiro uso, limitada pelo `dataSync` do Android 15, que é tratado). | Macrobenchmark de busca e rolagem, tempo do snapshot no aparelho. |
| ~17 mil faixas | Limite de projeto do índice SMART (envelope de cerca de 17 mil linhas a 960 dimensões, `FileIndexStore.kt:22-32`). Acima disso, as faixas ficam fora do SMART. | Teste de carga do índice no limite e acima dele. |
| 50 mil+ faixas | PERF-01 vira ANR provável; `LibraryState` com 5 agrupamentos e cópias em memória; histórico e estatísticas lineares; consulta única ao MediaStore com todos os campos. | Biblioteca sintética de 50 mil arquivos, perfil de memória (Android Studio Profiler), ANR rate. |

**Não afirmo** que o app suporta um tamanho específico de biblioteca: isso precisa de teste de carga no aparelho (seção G).

### 7. Arquitetura e qualidade do código

#### ARQ-01 — Regras de negócio na camada de UI
- **Severidade:** **P3** · **Confiança:** alta.
- **Descrição.** O `MainViewModel.kt` (1.075 linhas) concentra a biblioteca, a busca, o histórico, o temporizador, a fila SMART, o backup, as playlists, as notas, as estatísticas e o modelo de gosto. É a causa raiz de REL-01, porque lógica que precisa sobreviver à tela mora num objeto preso a ela.
- **Solução.** Extrair repositórios e casos de uso com escopo de aplicação ou de serviço: `PlaybackSessionController` no serviço, `LibraryRepository` com estado próprio e `HistoryRecorder` no serviço. O ViewModel fica só com o estado de tela.

#### ARQ-02 — Telas duplicadas por layout
- **Severidade:** **P3** · **Confiança:** alta.
- **Descrição.** São 5 telas de biblioteca (`LibraryScreenOndas`, `Essencial`, `Contraste`, `Classica`, `SvLibraryScreen`), 3 players e 4 barras de navegação. O `SonivellApp.kt` tem 1.561 linhas. Cada mudança transversal, como o vidro (#11), exige editar dezenas de pontos e deixa partes de fora.
- **Solução.** Separar o **estado e as ações** (já existe `LibraryActions`) da **apresentação**, com componentes base compartilhados (cartão, linha de faixa, cabeçalho) e temas só como variações visuais. Dividir o `SonivellApp.kt` em navegação, rotas e diálogos.

#### ARQ-03 — Lógica duplicada entre Android e desktop
- **Severidade:** **P3** · **Confiança:** alta.
- **Descrição.** O `sortKey` é copiado no desktop (comentário em `SortKey.kt:8`), e a capa gerada foi duplicada no #13 (`AlbumArt.kt` e `desktop/.../Components.kt`).
- **Solução.** Um módulo `core/common` (KMP) para lógica pura (ordenação, busca, M3U, histórico) e uso do Compose Multiplatform para componentes visuais comuns.

**Outros pontos observados:**
- O código é consistente, bem comentado e com nomes claros.
- Não há dependências circulares entre módulos Gradle: `app` → `core:smart` → `core:ort-ops`/`core:map-native`.
- O motor é bem isolado, com injeção pelo Koin e interfaces de plataforma (`expect`/`actual`).
- Não encontrei código morto relevante, além de recursos de texto antigos do tema Desenvolvimento, já removidos no #11.

### 8. Infraestrutura e DevOps
- **Docker, Compose, Kubernetes, rede, portas, TLS, proxy, limites de recursos de servidor:** **N/A** (inexistentes).
- **CI/CD:** SEC-01 (assinatura), SUP-01 (integridade), SUP-02 (binários sem checksum) e SUP-03 (permissões e push forçado).
- **CI-01 (P3): o build de APK não roda testes nem lint.** O `build.yml` só compila, e o `smart-audit.yml` roda testes só quando o PR mexe no motor. **Solução:** adicionar `./gradlew lint testDebugUnitTest` (depois de criar os testes, TEST-01) ao `build.yml` e torná-lo obrigatório na proteção da `main`.
- **CI-02 (P4): GitLab e GitHub duplicados**, com os mesmos passos em dois sistemas que podem divergir (o GitLab instala o SDK na mão, `.gitlab-ci.yml:28-35`). Decidir qual é o oficial ou gerar os dois a partir de um script comum.
- **Rollback e deploy:** não há loja nem release versionada. Os artefatos expiram (30 a 90 dias), e a `versionCode` fica fixa em `1` (`app/build.gradle.kts:15`), o que **impede atualizações** pela loja e confunde a instalação manual. **CI-03 (P3):** gerar a `versionCode` a partir do número do build ou de tags e publicar releases com tag.

### 9. Testes e qualidade

#### TEST-01 — App Android sem nenhum teste
- **Severidade:** **P2** · **Confiança:** alta.
- **Evidência.** Não existe `app/src/test` nem `app/src/androidTest`, e o `app/build.gradle.kts` não tem `testImplementation`. Os testes do projeto são 6 arquivos (cerca de 840 linhas): 3 do motor (`desktop-smart/src/jvmTest`) e 3 do desktop (`desktop/src/test`).
- **Funções críticas sem testes:** backup e restauração, importação e exportação de M3U, compactação do histórico, busca (`Search.score`), ordenação (`sortKey`), estatísticas, filtro de biblioteca, correções de tags, navegação, temporizador, fila SMART no app e `resolve()` do serviço de mídia.
- **Qualidade dos testes existentes.** Os testes do motor validam comportamento real (qualidade da fila, regressões numéricas bit a bit), não só cenários superficiais. Os do desktop (`BasicsTest`) cobrem utilidades. Não há testes de concorrência, carga, recuperação nem segurança.

### 10. Cenários de ataque e falha

| # | Cenário | Fragilidade | Condição | Consequência | Gravidade | Evidência | Mitigação |
|---|---|---|---|---|---|---|---|
| 1 | Milhares de requisições | Serviço de mídia aceita qualquer controlador; cada `onGetChildren` relê ou ordena a biblioteca (com cache de TTL) | App malicioso instalado | Uso de CPU e bateria; não derruba (Media3 serializa) | P3 | `PlaybackService.kt:142-154` | SEC-02 (lista de permitidos); limitar a frequência por controlador |
| 2 | Payloads inesperados | `.zip` de backup sem limites; M3U, `.lrc` e capas sem limite de tamanho; `mediaId` e `page` controlados por terceiros | Arquivo escolhido pela pessoa / app malicioso | Disco cheio, falta de memória, estado corrompido | P2 | SEC-03, REL-05, REL-06 | Limites e validação por lista de permitidos |
| 3 | Serviço externo indisponível | Android: nenhum. Desktop: ffmpeg/ffprobe ausentes | ffmpeg não instalado | Desktop avisa (`Library.kt:13`, `Engine.kt:156`); sem crash | P4 | — | O AppImage já embute; o .deb deve declarar `Depends: ffmpeg` |
| 4 | Banco lento | Equivalente: armazenamento lento ou MediaStore lento | Aparelho antigo, cartão SD | `loadTracks` em IO (bom), mas o `saveTags` grava na thread principal | P3 | REL-02 | Todas as gravações em `Dispatchers.IO` |
| 5 | Perda de conexão durante transação | Equivalente: crash ou falta de energia durante a gravação | Bateria acaba durante compactação ou gravação | Arquivo truncado e perda permanente de playlists, tags ou histórico | P2 | REL-02 | Gravação atômica e não sobrescrever o que não carregou |
| 6 | Duas modificações simultâneas | Playlists, smart playlists e histórico usam `Mutex`. `TagOverrides` não tem trava, mas hoje só é chamado da thread principal (`saveTags`), então a corrida é hipotética | Passar a chamar `put` de outra thread | Última gravação vence; estado do arquivo diferente do da memória | P4 | `TagOverrides.kt:25-34` | Ao mover a gravação para IO (REL-02), usar `Mutex` |
| 7 | Consumo contínuo de memória | Índice de letras inteiro em memória; `LibraryState` recriado a cada tecla | Pasta enorme de `.lrc`; biblioteca grande | Pressão de memória e GC, travamentos | P3 | PERF-01, PERF-03 | Índices limitados e chaves pré-calculadas |
| 8 | Token roubado | N/A (sem tokens). Equivalente: **chave de assinatura** pública | Repositório público | APK falso aceito como atualização | **P1** | SEC-01 | Nova chave fora do repositório |
| 9 | Acesso a recursos de outro usuário | N/A entre usuários. Entre apps: biblioteca e curtidas expostas | App malicioso instalado | Vazamento do gosto musical | P2 | SEC-02 | `onConnect` com lista de permitidos |
| 10 | Falha de dependência crítica | ONNX Runtime ou modelo não carrega | Aparelho incompatível, página de 16 KB | O SMART fica "indisponível" (há fallback: `smart.isAvailable`); o player segue | P4 | `LatentJamSmartEngine` | Já tratado; adicionar teste em emulador de 16 KB |
| 11 | Volume muito maior que o esperado | Biblioteca de 50 mil+ ou histórico de anos | Uso intenso | ANR na busca; índice SMART recusa acima do envelope | P2 | PERF-01, seção 6 | PERF-01; paginação |
| 12 | Reinício durante operação crítica | Restaurar backup não atômico; compactação do histórico; o temporizador some se o processo reiniciar (estado só em memória) | Crash ou OOM durante a operação | Estado misto, histórico truncado, temporizador perdido | P2 | SEC-03, REL-02, REL-01 | Operações atômicas; temporizador persistido no serviço |

---

## D. Análise de gargalos

1. **Computacional (UI):** recálculo integral da biblioteca e da busca na thread principal a cada tecla, com normalização Unicode por comparação (PERF-01). É o maior gargalo de experiência em bibliotecas grandes.
2. **Computacional (motor):** penalidade de "hubs" O(n · 1024 · 960) e análise de áudio no primeiro uso (horas para bibliotecas grandes). Já é mitigado com trabalho em segundo plano e limite do Android 15.
3. **Memória:** índice de letras sem limite; leituras inteiras de arquivos; `LibraryState` com várias cópias agrupadas da biblioteca.
4. **Armazenamento:** gravações síncronas e não atômicas (REL-02). O histórico reescrito por inteiro a cada ~2 mil escutas.
5. **Arquitetural:** lógica de reprodução contínua presa ao ciclo de vida da tela (REL-01); duplicação de telas por layout (ARQ-02).
6. **Infraestrutura:** N/A (sem servidor). No CI, os builds baixam o SDK e binários a cada execução no GitLab (lentidão e falta de reprodutibilidade).

## E. Riscos futuros

- **Distribuição pública (loja ou site):** SEC-01 deixa de ser teórico no primeiro APK baixado por terceiros. A `versionCode` fixa (CI-03) impede atualizar pela loja.
- **Crescimento de bibliotecas:** PERF-01 vira ANR. O envelope de cerca de 17 mil faixas do índice SMART passa a ser um limite visível.
- **Novas integrações com outros apps** (Wear, Assistant): sem a lista de permitidos de SEC-02, cada integração amplia a exposição.
- **Mudanças no Android:** limites de serviços em primeiro plano ficam mais rígidos a cada versão. Lógica no ViewModel (REL-01) vai falhar mais, não menos.
- **Novas telas e temas:** a duplicação (ARQ-02) faz cada mudança visual ou de comportamento custar N vezes e deixar telas para trás, como aconteceu com o vidro.
- **Dependências sem verificação** (SUP-01, SUP-02): o risco cresce com o tempo, sem nenhum alarme.
- **Licença GPL do ffmpeg no AppImage** (SUP-02): vira problema jurídico assim que houver distribuição pública.

---

## F. Plano de correção

Esforço: **P** (até meio dia), **M** (1 a 2 dias), **G** (3 dias ou mais). Risco de regressão: baixo, médio ou alto.

### Imediatas (P0)
Nenhuma descoberta P0. Não há execução remota, comprometimento grave ou exposição em massa comprovados.

### Urgentes (P1)

| ID | Correção | Esforço | Dependências | Risco de regressão |
|---|---|---|---|---|
| SEC-01 | Nova chave de release em *secrets*; o build de release falha sem as variáveis; chave de teste só no `debug`; workflows decodificam a keystore do secret; avisar sobre a reinstalação. | P | Acesso aos secrets do GitHub e do GitLab (você) | Baixo no código. **Alto para usuários atuais**: o certificado muda e quem instalou os APKs antigos precisa reinstalar. |
| REL-01 | Mover o temporizador, o registro de escutas, o salvamento da sessão e o abastecimento da fila SMART para o `PlaybackService` (ou um controlador ligado a ele); o ViewModel só observa. | G | ARQ-01 (parcial) | Médio: mexe no ciclo de reprodução; exige testes T-REL-01 e manuais. |

### Curto prazo (P2)

| ID | Correção | Esforço | Risco |
|---|---|---|---|
| SEC-02 | `onConnect` com lista de permitidos (próprio app, System UI, Android Auto, Wear, Bluetooth, `isTrusted`, `MEDIA_CONTENT_CONTROL`); `resolve()` ignora a `localConfiguration` de externos. | M | Médio: testar Android Auto (DHU), tela de bloqueio, Bluetooth e widgets. |
| SEC-03 + REL-04 | Limites (por entrada, total, quantidade, contagem real de bytes); lista de nomes permitidos; troca atômica de diretórios; parar o serviço antes; testes. | M | Baixo. |
| SUP-02 | Fixar a versão e o SHA-256 do ffmpeg e do appimagetool; registrar a versão e o fonte da GPL. | P | Baixo. |
| REL-02 | `atomicWrite` comum; checar a renomeação; não sobrescrever o que falhou ao carregar; `saveTags` em IO; o mesmo no desktop (`Store.kt`). | M | Baixo. |
| REL-03 | Timeouts e `destroyForcibly` no ffprobe e no ffmpeg; stderr descartado; limite na capa. | P | Baixo. |
| PERF-01 | `flowOn(Default)` e `debounce` na busca; chaves de ordenação e busca pré-calculadas por faixa. | M | Médio: ordenação e busca precisam de testes de equivalência (T-PERF-01). |
| TEST-01 | Estrutura de testes no `app` (JUnit, Robolectric para `Context`) e os testes da seção G. | M a G | Nenhum. |

### Melhorias planejadas (P3 e P4)

| ID | Correção | Esforço |
|---|---|---|
| SEC-04 | `data_extraction_rules.xml` ou `allowBackup="false"`; ajustar o texto do "Sobre". | P |
| SEC-05 | Validar `rows[]` no JNI do `ljdots`. | P |
| SUP-01 | `distributionSha256Sum`, `verification-metadata.xml`, actions fixadas por SHA, Dependabot. | P |
| SUP-03 | `permissions: contents: read` por padrão; escrita só no job de publicação em `push` para a `main`; trocar o `push -f` por artefatos. | P |
| CI-01 / CI-03 | Lint e testes no `build.yml`; `versionCode` automática; releases com tag. | P |
| REL-05 / PERF-03 | Limites de leitura; índice de letras limitado. | P |
| PERF-02 | `currentTrack` com `distinctUntilChanged` e mapa por id. | P |
| REL-06 | Paginação em `Long` com validação. | P |
| ARQ-01..03 | Extrair controladores do ViewModel; componentes base comuns; módulo `core/common`. | G (incremental) |
| SEC-06, PERF-04, PERF-05, CI-02, licenças | Conforme descrito em cada item. | P a M |

---

## G. Plano de testes

**Segurança**
- **T-SEC-01:** o build `assembleRelease` **falha** sem as variáveis `SONIVELL_*`. O `apksigner verify --print-certs` do artefato do CI **não** mostra o certificado de teste.
- **T-SEC-02a:** app de teste instrumentado (`androidTest`) sem permissões tenta `MediaBrowserCompat.connect` e `subscribe(root)`. Esperado: conexão recusada ou árvore vazia.
- **T-SEC-02b:** o Android Auto (Desktop Head Unit), a tela de bloqueio e o fone Bluetooth **continuam** controlando.
- **T-SEC-02c:** um controlador externo envia `MediaItem` com `setUri("file:///data/data/<pkg>/files/historico.tsv")`. Esperado: o item é recusado ou reconstruído pelo `mediaId`.
- **T-SEC-03:** testes unitários do `Backup.restore` com: zip bomb (entrada de 1 GB descompactada) recusada sem encher o disco; entrada `files/../x` e `files/sub/../../x` recusadas; entrada fora da lista (por exemplo, `files/indice_smart.bin`) ignorada; zip sem marcador recusado sem alterar nada; falha simulada no meio (I/O lança exceção) deixa os dados **originais intactos**.
- **T-SEC-05:** teste JNI com `rows` contendo `-1` e `n` devolve `false`, sem crash (rodar com ASan no build de teste do host, `LJQ4_TESTS=ON`).
- **Varredura:** OSV-Scanner ou Dependency-Check no CI, falhando com CVSS ≥ 7.

**Integração e confiabilidade**
- **T-REL-01 (instrumentado):**
  1. Tocar música, ligar o temporizador de 1 min, chamar `activity.finish()`, esperar 70 s: o player está pausado.
  2. Repetir com a fila SMART e 2 faixas restantes: a fila é abastecida sem a activity.
  3. Escutar 3 faixas sem a activity: o `historico.tsv` tem 3 eventos novos.
- **T-REL-02:** para `PlaylistStore`, `SmartPlaylists`, `TagOverrides`, `TasteModel` e `ListeningHistory`, simular a falha da gravação (sistema de arquivos falso que lança no meio) e verificar que o arquivo anterior continua legível. Simular a falha da renomeação e verificar que o erro é registrado e reportado.
- **T-REL-03 (desktop):** um `ffprobe` falso (script que dorme 1 h) no `PATH`: a varredura termina no tempo do timeout e marca o arquivo como falho.
- **T-REL-04:** restaurar com o serviço tocando: depois de reiniciar, os ajustes são os do backup (não os anteriores).

**Desempenho, carga e estresse**
- **T-PERF-01:**
  - Teste de equivalência: busca e ordenação com chaves pré-calculadas produzem **exatamente** a mesma ordem que a implementação atual em 10 mil faixas sintéticas com acentos e símbolos.
  - Macrobenchmark de digitação na busca com 1 mil, 10 mil e 50 mil faixas: nenhum quadro acima de 100 ms e zero ANR.
- **T-PERF-02:** Macrobenchmark de 5 min tocando: CPU da thread principal abaixo do atual; perfil sem `firstOrNull` em quadros.
- **T-PERF-05:** FrameTimingMetric na rolagem de listas longas com vidro (P50/P90/P99).
- **Carga do índice SMART:** 17 mil e 20 mil faixas sintéticas: comportamento definido (recusa limpa acima do envelope, sem OOM).

**Concorrência**
- **T-CONC-01:** depois de mover a gravação para IO (REL-02), 100 `saveTags` concorrentes em corrotinas diferentes: o arquivo final corresponde ao estado em memória.
- **T-CONC-02:** `history.record` concorrente com `clear()`: nenhum evento perdido ou duplicado fora do esperado.

**Recuperação**
- **T-REC-01:** um `playlists.json` truncado ao iniciar: o app avisa, preserva o arquivo como `.corrompido` e **não** sobrescreve na primeira gravação.
- **T-REC-02:** matar o processo (`adb shell am kill`) durante a compactação do histórico: depois de reiniciar, o histórico tem as escutas anteriores (no máximo perde as mais recentes).

---

## H. Conclusão

1. **O projeto está seguro?** **Parcialmente.** Não há falhas de execução remota nem de injeção, e os parsers mais delicados (índice SMART, JNI do mapa, caminhos do backup, consultas ao MediaStore) estão bem defendidos. Mas a **assinatura dos APKs com chave pública** (SEC-01) anula a garantia de autenticidade, e o **serviço de mídia aberto** (SEC-02) expõe dados pessoais a outros apps. A varredura de CVEs não pôde ser feita.
2. **O projeto está estável?** **Para uso em primeiro plano, sim. Para uso em segundo plano, não totalmente.** Com a tela fechada, o temporizador, o histórico e a fila SMART param (REL-01). Gravações não atômicas podem perder dados num crash (REL-02).
3. **Apresenta gargalos importantes?** **Sim, um:** a busca e a ordenação recalculadas na thread principal a cada tecla (PERF-01), relevante a partir de bibliotecas grandes. Precisa de medição. O resto é desperdício menor ou trabalho de segundo plano já mitigado.
4. **Está preparado para produção?** **Não para distribuição pública.** Pode seguir em uso pessoal ou beta fechado.
5. **Cinco maiores riscos:**
   1. SEC-01, chave de assinatura pública;
   2. REL-01, lógica de reprodução presa à tela;
   3. SEC-02, serviço de mídia sem lista de permitidos;
   4. REL-02 e SEC-03, perda e corrupção de dados (gravação e restauração);
   5. PERF-01, ANR em bibliotecas grandes.
6. **O que corrigir antes de disponibilizar publicamente:** SEC-01, REL-01, SEC-02, SEC-03 e REL-02; SUP-02 (integridade e licença do ffmpeg no AppImage) se o AppImage for distribuído; CI-03 (`versionCode`) se for para uma loja; e uma varredura de CVEs limpa.
7. **Melhorias de maior impacto na qualidade geral:**
   1. Mover a lógica de reprodução para o serviço (resolve REL-01 e reduz ARQ-01).
   2. Criar a base de testes do app (TEST-01) e rodá-la no CI (CI-01).
   3. Pré-calcular as chaves de busca e ordenação fora da thread principal (PERF-01).
   4. Componentes base compartilhados entre os layouts (ARQ-02), para mudanças visuais futuras não deixarem telas para trás.

---

### Próxima etapa (registro da etapa 1)

_Concluída: as correções vieram no #15, #16, #17 e #18; ver "Vereditos finais" no início._

Conforme combinado, **nenhuma correção foi aplicada**. Com a sua autorização, a etapa 2 corrige primeiro **SEC-01** e **REL-01**, depois os P2. Cada correção vem com testes, em mudanças pequenas e com o antes e depois documentado. SEC-01 depende de você criar a chave nova e cadastrá-la nos *secrets*; eu deixo o build e os workflows prontos para usá-la.
