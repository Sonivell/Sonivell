# Backup do código antes do redesign (11/11/2026)

Cópia do código do app como estava no commit `6754aee` ("Player Vidro como padrão, correções do vidro
e tema claro iOS"), antes de aplicar o redesign visual em todos os temas.

- `app/src/main`, `app/src/lite`, `app/src/test`: app Android completo (código, recursos e testes).
- `app/src/smart`: código e recursos da edição SMART. Os modelos em `app/src/smart/assets` (21 MB)
  não foram copiados: não são código e não mudam com o redesign.
- `desktop/`: código da versão para PC.

Esta pasta **não entra no build** (o Gradle só compila `app/src` e `desktop/src`).

Para restaurar um arquivo, copie-o de volta para o mesmo caminho fora de `backup/`. Para restaurar
tudo de uma vez pelo git: `git checkout 6754aee -- app/src desktop/src`.
