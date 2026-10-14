# 5. Git, branches e CI

## Criar e publicar
```bash
cd datalume
git init -b main
git add . && git commit -m "chore: fase 0 e 1 (fundação)"
git remote add origin git@github.com:<sua-org>/datalume.git
git push -u origin main
```

## Padrão de branches (GitHub Flow enxuto)
- `main`: sempre implantável; protegida (PR obrigatório + CI verde).
- `feature/<fase>-<assunto>` ex.: `feature/f2-download-receita`
- `fix/<assunto>` · `chore/<assunto>` · `docs/<assunto>`
- Sem `develop` por enquanto (equipe pequena). O CI também roda em `develop`, caso adote depois.
- Merge por **squash**; título do PR no padrão Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`.

## Proteções recomendadas em `main` (Settings → Branches)
- Require a pull request before merging
- Require status checks: `build`
- Require linear history

## CI (`.github/workflows/ci.yml`)
Roda `mvn verify` com Amazon Corretto 17 em push/PR. Os testes de integração sobem um Postgres via
Testcontainers (o runner `ubuntu-latest` já tem Docker). O Dependabot está configurado para Maven e Actions.

## Java fixo
- `.sdkmanrc` fixa `17.0.13-amzn` (rode `sdk env install` e depois `sdk env`). Se a versão não existir mais
  no SDKMAN, use `sdk list java | grep amzn` e atualize o arquivo.
- O `maven-enforcer-plugin` falha o build fora do Java 17, para ninguém compilar com 21 por engano.
- O wrapper do Maven não está incluído: rode uma vez `mvn -N wrapper:wrapper -Dmaven=3.9.9` e faça commit de `mvnw` e `.mvn/`.
