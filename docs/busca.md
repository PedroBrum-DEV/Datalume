# Busca por nicho e região

Pesquisa de estabelecimentos por CNAE e região, com filtros, facetas, paginação por cursor, nichos salvos por
conta e exportação CSV. Tudo em Postgres (índices B-tree, GIN e trigram), sem motor de busca externo.

## Endpoints (todos exigem login)

| Método | Rota | Para quê |
|---|---|---|
| GET | `/api/busca/estabelecimentos` | busca livre (exige ao menos `cnae`, `uf`, `municipio` ou `nome`) |
| GET | `/api/busca/facetas` | contagens por município, CNAE e porte do recorte |
| POST/GET | `/api/nichos` | criar / listar nichos da conta |
| GET/PUT/DELETE | `/api/nichos/{id}` | ler / alterar / remover |
| GET | `/api/nichos/{id}/empresas` | empresas do nicho (aceita os filtros extras abaixo) |
| GET | `/api/nichos/{id}/facetas` | facetas do nicho |
| POST | `/api/nichos/{id}/exportacoes` | baixa o CSV do nicho |
| GET | `/api/exportacoes` | histórico de exportações da conta |

## Parâmetros de busca

Listas aceitam vírgula ou repetição (`cnae=8630,4635` ou `cnae=8630&cnae=4635`).

| Parâmetro | Exemplo | Observação |
|---|---|---|
| `cnae` | `8630504` ou `8630` | prefixo de 2 a 7 dígitos; pontuação ignorada (só na busca livre) |
| `secundarios` | `true` | também considera CNAEs secundários (mais cobertura, mais ruído) |
| `uf` | `SP,RJ` | |
| `municipio` | `3550308` | código IBGE |
| `situacao` | `02,08` ou `todas` | padrão: só ativas (`02`) |
| `porte` | `01,03` | 00 não informado · 01 micro · 03 pequeno · 05 demais |
| `matriz` | `true` | matriz ou filial |
| `abertoDe` / `abertoAte` | `2025-01-01` | data de início da atividade |
| `comEmail` / `comTelefone` | `true` | |
| `nome` | `sorriso` | trecho do nome fantasia ou razão social (mín. 3 caracteres) |
| `contatos` | `true` | inclui e-mail e telefone nos itens (padrão: não) |
| `limite` | `50` | 1 a 200 |
| `cursor` | `proximoCursor` | da página anterior |

Em `/api/nichos/{id}/empresas`, `cnae`, `uf`, `municipio` e `secundarios` vêm do nicho; os demais filtros refinam.

## Resposta

```json
{ "itens": [ { "cnpj": "...", "cnpjFormatado": "...", "razaoSocial": "...", "nomeFantasia": "...",
               "situacaoDescricao": "Ativa", "dataInicioAtividade": "2025-10-01", "cnaePrincipal": "8630504",
               "municipio": "São Paulo", "uf": "SP", "temEmail": true, "temTelefone": true } ],
  "proximoCursor": "MjAyNS0xMC0wMXxQMDAwMDAwMTAwMDEwMA", "total": 8231, "totalExato": true }
```
- **Ordenação**: abertura mais recente primeiro (sem data por último), CNPJ como desempate.
- **Cursor** (keyset): estável mesmo se a base mudar entre páginas; não repete nem perde itens. Trate como opaco.
- **Total**: só na 1ª página; exato até 10.000, depois `total: 10000` com `totalExato: false` ("10.000+").
- **Facetas**: top 20 por município, CNAE e porte; sobre uma amostra de até 100 mil resultados (`parcial: true` se passar disso).

## Nichos

Um nicho é um recorte salvo: CNAEs (prefixos) + UFs e/ou municípios, com opção de incluir secundários.
- Limite de nichos por plano (`GRATUITO` 1 · `PILOTO` 3 · `PROFISSIONAL` 10).
- CNAEs, UFs e municípios são validados contra a Referência (se o catálogo estiver carregado).
- Nome único por conta (sem diferenciar maiúsculas). Um nicho de outra conta responde 404.

## Exportação

`POST /api/nichos/{id}/exportacoes` devolve CSV (`;`, UTF-8 com BOM, abre direto no Excel).
- Limite de linhas por exportação conforme o plano: `GRATUITO` 100 · `PILOTO` 5.000 · `PROFISSIONAL` 50.000
  (cabeçalho `X-Limite-Exportacao`). Exportar de novo não soma: é limite por arquivo.
- **Sem e-mail e telefone**, a menos que `contatos=true` (podem ser dados pessoais de MEI).
- Toda exportação fica registrada (`/api/exportacoes`): quem, qual nicho, critérios, linhas e se levou contatos.
- Células que começam com `=`, `+`, `-` ou `@` são neutralizadas (injeção de fórmula no Excel).

## Como a busca é rápida

- Prefixos de CNAE viram códigos exatos (via catálogo) e usam `cnae_principal IN (...)`; com `secundarios=true`
  entra o GIN em `cnaes_secundarios`. Sem o catálogo de CNAE carregado, cai em `LIKE` (correto, porém sem índice):
  **carregue os CNAEs** (`DATALUME_REFERENCIA_CARGA_INICIAL=true`).
- Índices (migração `V4`): parcial de ativas, GIN de secundários, expressão de ordenação por abertura, trigram de nome.
- Contagem limitada a 10.001 linhas; sem `OFFSET`.

## Medir desempenho

```powershell
docker compose up -d db
.\scripts\sinteticos\gerar.ps1 -Quantidade 2000000     # dados sintéticos (marcados com referência 0000-00)
.\scripts\sinteticos\gerar.ps1 -Explain                # EXPLAIN ANALYZE das consultas típicas
.\scripts\sinteticos\gerar.ps1 -Limpar                 # remove os sintéticos
mvn verify "-Dperf=true" "-Dperf.linhas=300000"        # teste automático: p95 < 300 ms
```
No `-Explain`, procure `Index Scan`/`Bitmap Index Scan`. `Seq Scan` em tabela grande indica índice não usado.
Carregar milhões de linhas com os índices GIN ligados leva vários minutos. A meta de 300 ms (p95) vale para o
recorte do piloto; valide também na escala que você pretende operar.

## Métricas e logs

- Micrometer: `datalume.busca.duracao` (tag `tipo` = pesquisa/facetas) e `datalume.busca.resultados`
  (pesquisa/exportacao), em `/actuator/prometheus` (admin da plataforma).
- Os logs trazem só contagens e identificadores; critérios de busca (como o texto de `nome`) não são logados.

## Critério de pronto

- [ ] `mvn verify` verde (inclui `ModularityTests`)
- [ ] Recorte do piloto (odontologia em SP) com p95 < 300 ms na base sintética
- [ ] Paginação sem repetir nem perder itens (coberto por teste)
- [ ] Conta no limite do plano não cria nichos além dele; exportação respeita o limite e fica registrada