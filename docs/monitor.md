# Monitor e alertas

O usuário marca um nicho como monitorado e passa a ser avisado do que mudou nele a cada atualização da Receita:
aberturas, baixas, mudanças de situação, de endereço e de CNAE. A Receita publica uma vez por mês, então os
alertas também saem uma vez por mês, logo após a importação.

## Fluxo

```
Importação concluída ──► ImportacaoConcluida ──► monitor registra a referência (PENDENTE) e processa em segundo plano
   │                                              │  casa as mudanças com as regras de cada nicho
   │ (durante a importação)                       ▼
   └─► estabelecimento_evento              monitor_resultado + monitor_resultado_item  (por nicho e referência)
        (antes × depois, por CNPJ)                │
                                                  ▼  AlertasGeradas
                                   alerta: feed (lido/não lido) + fila de e-mails (um resumo por usuário e referência)
```

## Detecção de mudanças

A consolidação da importação compara o arquivo novo com o estado atual do banco **antes** de gravar (mesmo SQL, mesma
transação) e grava em `estabelecimento_evento` o estado antes e depois.

| Tipo | Quando |
|---|---|
| `ABERTURA` | empresa nova na base, ativa, com início de atividade nos **últimos 90 dias** da referência |
| `BAIXA` | passou a situação baixada (08) |
| `SITUACAO` | outra mudança de situação (nula, suspensa, inapta, reativada) |
| `MUDANCA_ENDERECO` | mudou UF, código de município da Receita ou CEP |
| `MUDANCA_CNAE` | mudou o CNAE principal |

- **A primeira referência importada não gera eventos** (senão toda a base seria "abertura"). Os eventos começam na
  segunda referência concluída. O critério é "existe importação concluída de **outra** referência".
- Contato e nome fantasia não geram evento (ruído e LGPD).
- Reimportar a mesma referência não duplica: a chave é (CNPJ, referência, tipo) e só linhas que mudaram são comparadas.
- Se você trocar o filtro da importação (outra UF ou CNAE), empresas que passam a entrar na base não geram evento,
  exceto aberturas recentes.
- Eventos são guardados por 24 meses (limpeza diária às 04:30; `datalume.estabelecimento.retencao-eventos-meses`).

## Nichos e direção

Um evento "casa" com o nicho se o estado **antes** ou **depois** estiver dentro dele (CNAE principal, UF, município).
Por isso o alerta diz também quando a empresa **saiu** do nicho:

| Direção | Significa |
|---|---|
| `ENTROU` | antes fora, depois dentro (ex.: abertura, ou trocou para um CNAE do nicho) |
| `SAIU` | antes dentro, depois fora (ex.: mudou de município) |
| `NO_NICHO` | continua dentro (ex.: baixa, suspensão) |

Eventos carregam só o **CNAE principal**; `incluirSecundarios` do nicho não vale para alertas.

## Regras de monitoramento

Cada tipo de evento em um nicho é uma regra. O limite do plano (`limite_alertas`) conta regras:
Gratuito 3 · Piloto 20 · Profissional 200. Ex.: o Gratuito (1 nicho) pode monitorar até 3 dos 5 tipos.

| Método | Rota | |
|---|---|---|
| PUT | `/api/nichos/{id}/monitoramento` | `{"tipos":["ABERTURA","BAIXA"]}` substitui as regras; lista vazia desliga |
| GET / DELETE | `/api/nichos/{id}/monitoramento` | |
| GET | `/api/nichos/{id}/mudancas?referencia=2026-10&tipo=ABERTURA` | consulta ao vivo, sem exigir monitoramento ativo |

## Feed e preferências (por usuário)

| Método | Rota | |
|---|---|---|
| GET | `/api/alertas?apenasNaoLidos=true&antesDe=<id>&limite=20` | mais recentes primeiro |
| GET | `/api/alertas/{id}` | resumo + itens (até 1.000) |
| GET | `/api/alertas/contagem` | `{"naoLidos": n}` |
| POST | `/api/alertas/{id}/lido` · `/api/alertas/lidos` | marca um / todos |
| GET / PUT | `/api/alertas/preferencias` | `{"emailAtivo": true|false}` |
| GET | `/api/alertas/cancelar?u=...&t=...` | link do e-mail, **público**, assinado (HMAC) |

Os alertas são da conta; cada usuário tem o seu estado de leitura.

## E-mail

- **Um resumo por usuário e referência** (único no banco). Lista até 50 itens (`max-itens-email`) e aponta para o feed.
- Traz CNPJ, nome e a descrição da mudança. **Nunca e-mail nem telefone das empresas.**
- Fila com tentativas e espera crescente (5, 10, 20… min, até 12 h); após 5 tentativas vira `FALHOU`.
- Cada e-mail leva o cabeçalho `List-Unsubscribe` e o link de cancelamento. Quem cancela deixa de receber resumos novos
  e os pendentes são cancelados; o feed continua. Reativar: `PUT /api/alertas/preferencias`.
- Alguns antivírus corporativos abrem links de e-mail; por isso o cancelamento é reversível.
- Em desenvolvimento use o **Mailpit** (`docker compose up -d`): SMTP em `localhost:1025` e caixa de entrada em
  http://localhost:8025. Em produção configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_AUTH=true`,
  `SMTP_STARTTLS=true`, `DATALUME_REMETENTE` e `DATALUME_URL_BASE`.

## Configuração

| Propriedade | Padrão | |
|---|---|---|
| `datalume.alerta.email-habilitado` | true | false = envios ficam PENDENTES (o feed funciona) |
| `datalume.alerta.url-base` | `http://localhost:8080` | base dos links do e-mail |
| `datalume.alerta.remetente` | `nao-responder@datalume.com.br` | |
| `datalume.alerta.segredo-links` | segredo do JWT | assina o link de cancelamento (mín. 32 caracteres) |
| `datalume.alerta.max-itens-email` | 50 | |
| `datalume.alerta.tentativas-envio` | 5 | |
| `datalume.alerta.intervalo-envio-ms` | 60000 | varredura da fila |
| `datalume.monitor.intervalo-pendencias-ms` | 600000 | retoma referências pendentes ou com falha |
| `datalume.monitor.retencao-meses` | 24 | retenção dos resultados |

## Operação

```bash
GET  /api/admin/monitor/execucoes                         # situação por referência (admin da plataforma)
POST /api/admin/monitor/processar  {"referencia":"2026-10","forcar":true}   # reprocessa em segundo plano
```
- Processar é **idempotente**: repetir a referência regrava os mesmos resultados e não duplica e-mails.
- Se a aplicação cair no meio, a referência fica `PENDENTE`/`PROCESSANDO`/`FALHOU` e o agendador a retoma (até 5 tentativas).
- Métricas: `datalume.monitor.mudancas`, `datalume.monitor.alertas`, `datalume.alerta.emails{status=enviado|falha}`.
  Os logs trazem só identificadores e contagens (sem e-mail de usuário nem dados de empresas).

## Limitações conhecidas

- Fonte única (Receita, mensal). PNCP e outras fontes, webhook, Slack e WhatsApp ficam para depois.
- Só os 5 tipos acima; sem alertas de contato ou de nome fantasia.
- Resultados guardam até 1.000 itens por nicho e referência (o total é sempre completo; o feed indica `itensTruncados`).

## Critério de pronto

- [ ] `mvn verify` verde (inclui `ModularityTests`)
- [ ] Duas importações de referências diferentes geram só as mudanças reais (`docker compose up -d`, importar, importar a seguinte)
- [ ] Um nicho monitorado recebe o resumo no Mailpit e o feed mostra os mesmos itens
- [ ] Reprocessar a referência (`forcar=true`) não duplica resultados nem e-mails
- [ ] O link de cancelamento interrompe os e-mails do usuário