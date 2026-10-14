# Tabelas de referência

## O que existe
| Tabela | Conteúdo | Origem |
|---|---|---|
| `uf` | 27 UFs (código IBGE, sigla, nome, região) | migração `V2__dados_basicos.sql` (já vem carregada) |
| `municipio` | ~5.570 municípios (código IBGE 7 dígitos) | API do IBGE (localidades) |
| `cnae_subclasse` | subclasses CNAE (7 dígitos) com classe/grupo/divisão/seção | API do IBGE (CNAE v2) |
| `municipio_receita` | código de município da Receita (4 dígitos) → código IBGE | arquivo `Municipios.csv` da Receita + conciliação |

## Como carregar
**Automático** (primeira subida): `DATALUME_REFERENCIA_CARGA_INICIAL=true mvn spring-boot:run`
Carrega municípios e CNAEs se as tabelas estiverem vazias. Falha de rede é registrada em log e não derruba a app.

**Manual** (endpoints de admin da plataforma; exigem usuário com `admin_plataforma = true`):
```bash
POST /api/admin/referencia/carga/ibge                         # municípios
POST /api/admin/referencia/carga/cnae                         # CNAEs
POST /api/admin/referencia/carga/municipios-receita           # multipart "arquivo" = Municipios.csv (ISO-8859-1)
POST /api/admin/referencia/carga/conversao                    # multipart "arquivo" = codigo_receita;codigo_ibge
GET  /api/admin/referencia/resumo                             # contagens
```
Para tornar seu usuário admin da plataforma (uma vez, direto no banco):
```sql
UPDATE usuario SET admin_plataforma = true WHERE lower(email) = lower('voce@empresa.com.br');
```
Faça login de novo para o token trazer o papel.

## Conversão Receita → IBGE (como funciona)
O `Municipios.csv` da Receita só traz código e nome, sem UF, e há nomes repetidos entre estados.
Por isso a conversão tem três camadas, da mais para a menos confiável:
1. **Tabela curada** (`/carga/conversao`, origem `TABELA`): se você tiver uma tabela oficial Receita↔IBGE, ela manda.
2. **Nome único no país** (origem `NOME_UNICO`): conciliado automaticamente na importação do `Municipios.csv`.
3. **Nome + UF na hora da consulta**: `ReferenciaConsulta.municipioIbgeDaReceita(codigoReceita, uf)`. Na Fase 2,
   o estabelecimento traz a UF e isso resolve os ambíguos sem tabela externa.

Depois da importação, `GET /api/admin/referencia/resumo` mostra `municipiosReceitaSemIbge`: são os que
dependem da camada 3. Para o piloto em SP isso não bloqueia.

## Consulta (qualquer usuário autenticado)
```
GET /api/referencia/ufs
GET /api/referencia/ufs/SP/municipios
GET /api/referencia/municipios?q=campinas&uf=SP
GET /api/referencia/cnaes?q=odontol           # por descrição
GET /api/referencia/cnaes?q=4635              # por prefixo do código
GET /api/referencia/cnaes/8630504      # código sem barra
```
