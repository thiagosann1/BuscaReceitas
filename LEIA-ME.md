# Busca Receitas (Android)

App Android para buscar receitas na internet: você digita "pão doce" e ele traz várias
receitas de sites brasileiros com **foto, ingredientes, modo de preparo, tempo e rendimento**.

## O que o app faz
- Busca "receita + termo" na internet (DuckDuckGo, com Bing como reserva)
- Abre os sites encontrados e lê a receita estruturada que eles publicam
  (TudoGostoso, Receitas Nestlé, Panelinha, Ana Maria Braga, Guia da Cozinha, etc.)
- As receitas vão aparecendo na lista conforme ficam prontas
- Tela da receita com ingredientes (dá para ir marcando ✔ enquanto cozinha) e passos numerados
- ♥ Favoritas salvas no celular, que abrem mesmo sem internet
- Compartilhar a receita em texto (WhatsApp etc.) e abrir o site original

## Como gerar o APK — opção 1: sem instalar nada (GitHub)
1. Crie uma conta em github.com (grátis) e um repositório novo.
2. Envie todos os arquivos desta pasta para o repositório
   ("Add file → Upload files", arrastando o conteúdo da pasta — inclusive a pasta `.github`).
3. Vá na aba **Actions**: o processo "Gerar APK" roda sozinho (uns 5 minutos).
4. Quando ficar verde, abra a execução e baixe **BuscaReceitas-apk** no fim da página.
5. Descompacte, passe o `app-debug.apk` para o celular e abra.
   O Android vai pedir para permitir "instalar apps desconhecidos" — é normal para apps fora da Play Store.

> Dica: o upload pelo navegador às vezes ignora pastas que começam com ponto.
> Se a aba Actions não mostrar nada, crie o arquivo `.github/workflows/build-apk.yml`
> pelo próprio site ("Add file → Create new file") e cole o conteúdo.

## Como gerar o APK — opção 2: Android Studio
1. Instale o Android Studio (developer.android.com/studio).
2. File → Open → escolha esta pasta. Espere o Gradle sincronizar.
3. Com o celular ligado no USB (depuração USB ativada), clique em ▶ Run.
   Ou: Build → Build App Bundle(s)/APK(s) → Build APK(s).

## Arquivos principais
- `app/src/main/java/com/receitas/busca/RecipeSearch.kt` — busca na internet e leitura das receitas
- `app/src/main/java/com/receitas/busca/MainActivity.kt` — telas
- `app/src/main/java/com/receitas/busca/RecipeViewModel.kt` — estado do app e favoritas
- `app/src/main/java/com/receitas/busca/Recipe.kt` — modelo da receita e armazenamento

## Observações
- Precisa de internet para buscar (as favoritas funcionam offline).
- Sites sem receita estruturada são ignorados, por isso podem vir menos resultados que sites encontrados.
- Se um buscador mudar o layout da página de resultados, ajuste as funções `duckDuckGo()` / `bing()` em `RecipeSearch.kt`.
