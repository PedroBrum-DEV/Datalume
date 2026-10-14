# Fase 0 — Preparação: checklist

Legenda: ✅ feito no repositório · 🧑 depende de você (consulta externa ou decisão de negócio) · 🔎 verificado, reconfirmar antes de depender

| # | Item | Status | Onde |
|---|------|--------|------|
| 1 | Verificar o nome Datalume (domínio, INPI 35/42, Junta, redes) | 🧑 roteiro pronto | [01-validacao-nome.md](01-validacao-nome.md) |
| 2 | Escolher 2–3 nichos e 1 região | 🧑 proposta pronta para você aprovar | [02-nichos-e-regiao-piloto.md](02-nichos-e-regiao-piloto.md) |
| 3 | Listar 5–10 empresas candidatas ao piloto | 🧑 modelo + perfis | [03-candidatas-piloto.md](03-candidatas-piloto.md) |
| 4 | Confirmar licença e endereço dos dados do CNPJ | 🔎 | [04-dados-abertos-cnpj.md](04-dados-abertos-cnpj.md) |
| 5 | Repositório Git, branches, CI | ✅ | [05-git-e-ci.md](05-git-e-ci.md), `.github/workflows/ci.yml` |
| 6 | Corretto 17 fixo + projeto Spring Boot 3.3 / Maven (`br.com.datalume`) | ✅ | `.sdkmanrc`, `pom.xml` (enforcer exige Java 17) |
| 7 | Postgres local com Docker Compose | ✅ | `docker-compose.yml` |

## Critério de pronto da Fase 0

- [ ] O projeto sobe localmente (`docker compose up -d db` + `mvn spring-boot:run`)
- [ ] O CI roda verde no primeiro push
- [ ] O nome está validado (itens 1.1 a 1.4 do arquivo 01 concluídos)
- [ ] Os nichos e a região do piloto estão definidos (arquivo 02 preenchido e aprovado)

Os itens 🧑 não foram feitos por mim porque exigem consulta em sistemas externos (INPI, Registro.br, Junta
Comercial) ou decisão sua. Eu deixei o caminho mapeado para cada um.
