# Importação da base de CNPJ (Receita Federal)

Baixa os arquivos mensais de dados abertos, carrega no Postgres e mantém `empresa` e `estabelecimento`
atualizados. Roda em segundo plano, é retomável e idempotente.

## Fluxo

```
Receita (AAAA-MM/*.zip) ──download com retomada──► ./dados/receita/AAAA-MM/
        └─► ZIP ─► COPY (LATIN1) ─► stg_* (staging) ─► conversão + upsert ─► empresa / estabelecimento
```

Ordem dos arquivos de um lote (importa só o necessário; Sócios, Simples, Países e Qualificações ficam de fora):

1. `Municipios.zip` → `municipio_receita` + conciliação com o IBGE
2. `Naturezas.zip` → `natureza_juridica` · `Motivos.zip` → `motivo_situacao`
3. `Estabelecimentos0..9.zip` → `estabelecimento`
4. `Empresas0..9.zip` → `empresa` (só empresas que têm estabelecimento carregado; por isso vêm depois)

Cada arquivo é **uma transação**. O lote para no primeiro erro; ao retomar, arquivos `CONCLUIDO` são pulados.

## Pontos de projeto

- **Carga rápida**: o CSV vai direto para o Postgres com `COPY ... ENCODING 'LATIN1'` (sem parser em Java).
- **Idempotência**: cada linha tem `row_hash` (md5 dos dados já tratados). Reimportar a mesma referência grava 0 linhas;
  na referência seguinte só são regravadas as linhas que mudaram. `referencia_alteracao` guarda a referência da última mudança.
- **Município**: o código da Receita vira IBGE por (1) tabela curada, (2) nome único no país, (3) nome + UF do estabelecimento.
  `GET /api/admin/importacao/resumo` mostra `semMunicipioIbge`.
- **CNPJ alfanumérico**: `cnpj_basico` e `cnpj` são texto e a validação do DV já aceita letras (regra oficial, valor = ASCII − 48).
- **Uma importação por vez**: índice único parcial no banco (`status = 'EXECUTANDO'`) + executor de uma thread.
- **Reinício**: se a aplicação cair, o lote fica `EXECUTANDO`; na próxima subida ele vira `FALHOU` e pode ser retomado.
- **Datas**: `0`, `00000000` e datas inválidas viram `NULL`.

## Filtro (importante para o piloto)

Sem filtro entram dezenas de milhões de estabelecimentos. Use variáveis de ambiente:

```bash
DATALUME_IMPORTACAO_UFS=SP
DATALUME_IMPORTACAO_CNAES=8630504,4635401,4635402,4635403,4635499   # prefixos do CNAE principal
```
No perfil `local` o padrão já é esse piloto (SP + odontologia + distribuidoras de bebidas). Para o Brasil inteiro,
defina as duas variáveis **vazias**. O filtro é aplicado na consolidação; o `COPY` ainda lê o arquivo inteiro, então o
tempo de leitura e o download não diminuem, só o tamanho do banco.

## Como rodar

```bash
docker compose up -d db
DATALUME_REFERENCIA_CARGA_INICIAL=true mvn spring-boot:run     # 1ª vez: municípios IBGE + CNAEs
# promova seu usuário a admin da plataforma (ver README) e faça login para pegar o token

curl -s localhost:8080/api/admin/importacao/referencias -H "Authorization: Bearer $TOKEN"   # meses publicados
curl -s -X POST localhost:8080/api/admin/importacao -H "Authorization: Bearer $TOKEN"       # mais recente
curl -s localhost:8080/api/admin/importacao/lotes/1 -H "Authorization: Bearer $TOKEN"       # progresso por arquivo
curl -s -X POST localhost:8080/api/admin/importacao/lotes/1/retomar -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/estabelecimentos/11222333000181 -H "Authorization: Bearer $TOKEN"
```
Referência específica: `POST /api/admin/importacao` com `{"referencia":"2026-10","forcar":false}`.

## Agendamento mensal

`DATALUME_IMPORTACAO_AGENDADA=true` liga uma verificação diária às 03:00 (`datalume.importacao.agendamento.cron`):
se há referência nova na Receita, importa; se a última tentativa dela falhou, retoma; se já concluiu, não faz nada.

## Configuração (`datalume.importacao.*`)

| Propriedade | Padrão | Para quê |
|---|---|---|
| `base-url` | diretório da Receita | pasta que contém `AAAA-MM/` |
| `diretorio` | `./dados/receita` | onde ficam os ZIPs (`DATALUME_IMPORTACAO_DIRETORIO`) |
| `partes` | 10 | quantidade de `EstabelecimentosN`/`EmpresasN` |
| `apagar-zip-apos-importar` | true | libera disco a cada arquivo |
| `tentativas-download` | 5 | cada tentativa retoma do `.part` |
| `meses-busca` | 6 | meses para trás ao procurar a referência mais recente |
| `filtro.ufs` / `filtro.cnae-prefixos` | vazio | restringe o que entra |

## Se algo falhar

| Sintoma | O que fazer |
|---|---|
| `Referência não encontrada na Receita` | Confirme a pasta `AAAA-MM` no endereço da Receita; o endereço pode ter mudado (ver `docs/fase-0/04-dados-abertos-cnpj.md`) e basta ajustar `base-url`. |
| Download lento/interrompido | Nada a fazer: ele retoma do `.part`. Se esgotar as tentativas, use `/retomar`. |
| Erro de COPY em um arquivo (linha malformada) | O arquivo fica `FALHOU` com a mensagem do Postgres e nada é gravado dele. Veja `GET /lotes/{id}`. |
| `ZipException` | O ZIP é apagado automaticamente; `/retomar` baixa de novo. |
| Lote preso em `EXECUTANDO` | Reinicie a aplicação (ele vira `FALHOU`) e use `/retomar`. |

## Cuidados

- **Primeira carga sem filtro** é pesada: planeje disco e tempo, e considere criar os índices depois (eles já existem na migração).
- **LGPD**: e-mail e telefone de MEI podem ser dados pessoais. A API de consulta os devolve; decida a política de exposição
  antes de abrir a plataforma a clientes, e mantenha atribuição à Receita (CC BY).
- **Fuso**: a referência mais recente é calculada em `America/Sao_Paulo`.

## Tabelas

`empresa`, `estabelecimento`, `natureza_juridica`, `motivo_situacao`, `importacao_lote`, `importacao_arquivo`
e as de passagem `stg_estabelecimento`, `stg_empresa`, `stg_codigo` (UNLOGGED, esvaziadas a cada arquivo). Migração: `V3__estabelecimento_e_importacao.sql`.

## Critério de pronto

- [ ] `mvn verify` verde (inclui o teste ponta a ponta com um servidor da Receita local)
- [ ] `POST /api/admin/importacao` conclui uma referência real com o filtro do piloto
- [ ] Repetir com `forcar=true` resulta em 0 inseridos e 0 atualizados nos estabelecimentos
- [ ] `GET /api/estabelecimentos/{cnpj}` devolve o detalhe de um CNPJ importado