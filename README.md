# InstaSave BR

Aplicativo Android nativo para baixar vídeos de publicações do Instagram (Reels, posts com vídeo e IGTV) diretamente no armazenamento do seu celular.

## Recursos

- Recebe links compartilhados pelo app do Instagram (`Compartilhar → InstaSave BR`).
- Cola manualmente uma URL do Instagram.
- Download real do arquivo de mídia (não salva a página HTML).
- Progresso, velocidade, tamanho baixado, botão Baixar/Cancelar.
- Continua baixando em segundo plano com notificação e ação de cancelar.
- Salva em `Movies/InstaSave BR` via MediaStore (Scoped Storage, Android 10+).
- Histórico de downloads com miniatura, data, tamanho, reproduzir, compartilhar e excluir.
- Player integrado com Media3 (ExoPlayer).
- Tema claro/escuro e Dynamic Color (Material 3).
- Erros amigáveis sem stack trace para o usuário.
- Login opcional dentro do app para posts que exigem sessão.

## Stack

Kotlin · Jetpack Compose · Material 3 · MVVM · Coroutines · WorkManager · Room · OkHttp · Media3

## Requisitos de desenvolvimento

- Android Studio (AGP 8.2.2, Gradle 8.5)
- compileSdk 34 · minSdk 29 (Android 10) · alvo de compatibilidade: Android 10–16

## Compilar

```bash
cd instasaver
./gradlew :app:assembleDebug        # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest         # testes
```

## Token de acesso oficial (oEmbed)

O app tenta, nessa ordem:

1. A API oficial oEmbed, quando um token for configurado em Ajustes.
2. A requisição pública da página do post com seu session cookie (se estiver logado).
3. A renderização da página em WebView (contorna a ausência de `og:video` no HTML estático da resposta).

Para aumentar a chance com a API oficial da Meta, você pode configurar um **token de acesso oficial** na tela **Ajustes → Token de acesso oficial (oEmbed)**. O app usa o endpoint oficial do Instagram:

```
GET https://graph.facebook.com/v19.0/instagram_oembed?url=<URL_DO_POST>&access_token=<TOKEN>
```

### Como o usuário obtém esse token

1. Acesse <https://developers.facebook.com> e faça login com a mesma conta do Facebook que administra a conta do Instagram.
2. Crie um app (tipo **Empresarial/Business** — ou, para uso pessoal, um app do tipo **Consumidor**, dependendo da experiência atual do Meta).
3. No painel do app, abra o produto **Instagram** e clique em **Configurar**.
4. Siga o fluxo **Configurar o Instagram Basic Display / Instagram API** e autentique com a sua conta do Instagram (Business ou Creator).
5. Em **Funções → Funções de teste**, adicione sua conta do Instagram como **Administrador** e aceite o convite no app do Instagram (Configurações → Funções).
6. Em **Tokens de acesso** do Meta, gere um token para esse usuário de teste selecionando as permissões necessárias (por exemplo: `instagram_basic` e `pages_show_list`).
7. Copie o token exibido e cole no campo **Token de acesso oficial (oEmbed)** em **Ajustes** do InstaSave BR.

> O token de teste pode expirar em ~60 dias; quando expirar, gere outro e salve de novo em Ajustes. Tokens de longa duração ficam disponíveis em **Configurations → App secret** e podem ser permutados com `https://graph.facebook.com/v19.0/oauth/access_token?grant_type=fb_exchange_token&...`.

## Login no Instagram (opcional)

Para vídeos cujo post não expõe `og:video` no HTML, o app oferece em **Ajustes** o botão **Entrar no Instagram**. Ao autenticar com o WebView interno:

- o `sessionid` e o `csrftoken` ficam apenas no DataStore local do dispositivo e são usados apenas para buscar a página;
- o `WebViewResolver` passa a renderizar essa página com sua sessão e extrai a URL `.mp4` embutida no JSON (`video_versions`);
- em **Sair da conta** os cookies são apagados do DataStore e do CookieManager.

## Privacidade

- O login do Instagram **é opcional**. Sem ele, o app ainda tenta obter mídia pública.
- O token oEmbed, se configurado, é guardado apenas no DataStore local do dispositivo e usado somente para chamar a API oficial da Meta.
- Nenhuma mídia é enviada para servidores próprios.
- O app não transforma o post HTML em vídeo: sempre busca a URL direta da mídia.

## Como funciona o download (resolução de mídia)

`InstagramMediaResolver` / `InstagramOEmbedResolver` / `WebViewResolver` são isolados para fácil troca:

1. Se houver token de acesso configurado em Ajustes, tenta primeiro a API oficial oEmbed.
2. Se houver sessão de Instagram conectada, faz a requisição pública da página do post com os cookies da sessão e extrai `og:video` / JSON embutido / `.mp4`.
3. Mesmo sem sessão, tenta extrair da página pública.
4. Fallback final: uma **WebView oculta** renderiza a página do post e extrai a URL `.mp4` diretamente da DOM/JSON (`video_versions`).
5. Se o Instagram não expuser a mídia (conteúdo privado que você não pode ver, bloqueio de acesso, mudança de layout), a UI informa de forma clara que não foi possível baixar.

O app **não contorna autenticação, DRM, conteúdo privado nem controles de acesso** do Instagram.

## Diagnóstico / Logcat

Todo o fluxo do resolver é logado com a tag `InstaSave`:

```bash
adb logcat | grep InstaSave
```

Principais linhas:

- `sourceUrl=..., loggedIn=true/false`
- `HTTP 200, finalUrl=..., hasOgVideo=..., hasVideoUrl=..., hasLogin=..., hasIsPrivateTrue=..., hasOgTitle=...`
- `resolve=ResolveResult$...`
- `trying WebView fallback`
- `webviewresolver debug={...}`
- `webviewresolver mp4 candidates: [...]`
- `webView fallback result=Success(...)`

## Estrutura

```
app/src/main/java/com/instasaver/app/
├── InstaSaveApp.kt
├── MainActivity.kt
├── data/
│   ├── downloader/  (parser, resolvers, WebViewResolver, DownloadWorker, receivers)
│   ├── repository/
│   ├── local/
│   └── model/
└── ui/
    ├── theme/ ├── home/ ├── download/ ├── history/ ├── settings/ └── player/
```

## Licença/instalação

APK de debug disponível em `app/build/outputs/apk/debug/app-debug.apk`.
