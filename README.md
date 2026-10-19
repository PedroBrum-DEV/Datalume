# Datalume

Diretório vivo de empresas por nicho e região, que se atualiza sozinho.
Fonte principal: base de CNPJ da Receita Federal (arquivos mensais). PNCP entra depois, como módulo complementar.

**Stack:** Java 17 (Amazon Corretto) · Spring Boot 3.3 · Spring Modulith · PostgreSQL 16 · Flyway · Maven

## Status

| Fase | Situação |
|---|---|
| 0 — Preparação | Infra pronta; itens de validação externa e decisão de negócio em [docs/fase-0](docs/fase-0/00-checklist.md) |
| Fundação | Implementada (ver abaixo) |
| Importação da Receita | Implementada ([docs/importacao.md](docs/importacao.md)) |

## Subir localmente

```bash
sdk env install && sdk env          # Corretto 17 (ou instale manualmente)
docker compose up -d db             # Postgres 16 em localhost:5432
DATALUME_REFERENCIA_CARGA_INICIAL=true mvn spring-boot:run
```
O perfil `local` é o padrão (segredo JWT de desenvolvimento embutido). Em produção use
`SPRING_PROFILES_ACTIVE=prod` e defina `DB_URL`, `DB_USER`, `DB_PASSWORD` e `JWT_SECRET` (mín. 32 caracteres);
sem `JWT_SECRET` a aplicação **não sobe**, de propósito.

Testes (precisam de Docker, usam Testcontainers): `mvn verify`

## Teste rápido da API

```bash
# cadastro (cria conta no plano GRATUITO + usuário ADMIN da conta)
curl -s -X POST localhost:8080/api/auth/cadastro -H 'Content-Type: application/json' \
  -d '{"nomeEmpresa":"Minha Empresa","nome":"Ana","email":"ana@exemplo.com.br","senha":"SenhaForte123"}'

# login -> accessToken
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@exemplo.com.br","senha":"SenhaForte123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s localhost:8080/api/conta/me -H "Authorization: Bearer $TOKEN"
curl -s "localhost:8080/api/referencia/cnaes?q=odontol" -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/actuator/health
```

## O que já está implementado

- **Módulos (Spring Modulith)**: `conta`, `referencia`, `importacao`, `estabelecimento`, `busca`, `monitor`, `alerta`
  + `compartilhado` (aberto). Dependências permitidas declaradas em cada `package-info.java` e verificadas por
  `ModularityTests`. `busca`, `monitor` e `alerta` têm só um marcador
  (`XxxModulo`) para existirem como módulo; apague-o ao criar a primeira classe real.
- **Flyway**: `V1__schema_inicial.sql` (conta, usuário, plano, referência) e `V2__dados_basicos.sql` (3 planos + 27 UFs).
- **Referência**: UFs, municípios IBGE, CNAEs (subclasses) e conversão município Receita→IBGE
  ([detalhes](docs/referencia.md)). Municípios e CNAEs são carregados da API do IBGE (a app precisa de internet na carga).
- **Autenticação**: Spring Security stateless, JWT HS256 (1 h), senhas em BCrypt. Papéis: `ADMIN`/`MEMBRO` (da conta)
  e `ADMIN_PLATAFORMA` (operação do Datalume; hoje promovido por SQL).
- **Conta/usuário/plano**: entidades e limites por plano (sem cobrança).
- **Observabilidade**: logs com `requestId` (header `X-Request-Id`), JSON no perfil `prod`; Actuator (`health` e `info`
  públicos, `metrics`/`prometheus` só para `ADMIN_PLATAFORMA`).
- **Erros**: RFC 7807 (`application/problem+json`) para validação, negócio, segurança e falhas inesperadas.

## Critério de pronto da fundação

- [ ] `mvn verify` verde (inclui verificação modular e testes de integração)
- [ ] `GET /api/admin/referencia/resumo` mostra 27 UFs, ~5.570 municípios e ~1.300 subclasses CNAE
- [ ] Cadastro, login e `/api/conta/me` funcionam; `/actuator/health` responde `UP`

## Importação da Receita

Download com retomada, carga via `COPY`, consolidação idempotente e consulta por CNPJ. Detalhes, filtros e
operação em [docs/importacao.md](docs/importacao.md).

```bash
curl -s -X POST localhost:8080/api/admin/importacao -H "Authorization: Bearer $TOKEN"     # admin da plataforma
curl -s localhost:8080/api/estabelecimentos/11222333000181 -H "Authorization: Bearer $TOKEN"
```

## Ainda não incluído (de propósito)

Refresh token e logout/revogação, recuperação de senha, verificação de e-mail, limite de tentativas de login,
multi-usuário por conta (convites), cobrança. Entram quando houver necessidade real.

## Estrutura

```
src/main/java/br/com/datalume/
  compartilhado/   erros (ProblemDetail), RequestIdFilter, utilitários
  conta/           entidades, JWT/Security, cadastro e login; API pública: ContaAtual
  referencia/      consultas (ReferenciaConsulta) e carga (internal)
  estabelecimento/ empresas e estabelecimentos: carga em massa, consulta por CNPJ
  importacao/      download, controle de lotes e agendamento
  busca/ monitor/ alerta/   (próximas etapas)
docs/fase-0/  docs/
```