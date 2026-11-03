-- Registro de mudancas detectadas na importacao (por CNPJ, referencia e tipo)
CREATE TABLE estabelecimento_evento (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cnpj             VARCHAR(14)  NOT NULL,
    referencia       VARCHAR(7)   NOT NULL,
    tipo             VARCHAR(20)  NOT NULL
                     CHECK (tipo IN ('ABERTURA', 'BAIXA', 'SITUACAO', 'MUDANCA_ENDERECO', 'MUDANCA_CNAE')),
    situacao_antes   VARCHAR(2),
    situacao_depois  VARCHAR(2),
    cnae_antes       VARCHAR(7),
    cnae_depois      VARCHAR(7),
    uf_antes         VARCHAR(2),
    uf_depois        VARCHAR(2),
    municipio_antes  INTEGER,
    municipio_depois INTEGER,
    detectado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (cnpj, referencia, tipo)
);
CREATE INDEX ix_evento_ref_tipo ON estabelecimento_evento (referencia, tipo);
CREATE INDEX ix_evento_detectado ON estabelecimento_evento (detectado_em);

-- Regras de monitoramento: um tipo de evento em um nicho = 1 regra (conta no limite_alertas do plano)
CREATE TABLE monitoramento (
    id         UUID        PRIMARY KEY,
    conta_id   UUID        NOT NULL REFERENCES conta (id) ON DELETE CASCADE,
    nicho_id   UUID        NOT NULL REFERENCES nicho (id) ON DELETE CASCADE,
    tipo       VARCHAR(20) NOT NULL
               CHECK (tipo IN ('ABERTURA', 'BAIXA', 'SITUACAO', 'MUDANCA_ENDERECO', 'MUDANCA_CNAE')),
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (nicho_id, tipo)
);
CREATE INDEX ix_monitoramento_conta ON monitoramento (conta_id);

-- Controle do processamento de cada referencia
CREATE TABLE monitor_execucao (
    referencia          VARCHAR(7)  PRIMARY KEY,
    status              VARCHAR(12) NOT NULL CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'CONCLUIDA', 'FALHOU')),
    tentativas          INTEGER     NOT NULL DEFAULT 0,
    nichos_processados  INTEGER,
    alertas_gerados     INTEGER,
    erro                TEXT,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    iniciado_em         TIMESTAMPTZ,
    finalizado_em       TIMESTAMPTZ
);

-- Resultado por nicho e referencia (base do feed e do e-mail)
CREATE TABLE monitor_resultado (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conta_id         UUID         NOT NULL REFERENCES conta (id) ON DELETE CASCADE,
    nicho_id         UUID         NOT NULL REFERENCES nicho (id) ON DELETE CASCADE,
    nicho_nome       VARCHAR(100) NOT NULL,
    referencia       VARCHAR(7)   NOT NULL,
    total            BIGINT       NOT NULL,
    total_aberturas  BIGINT       NOT NULL DEFAULT 0,
    total_baixas     BIGINT       NOT NULL DEFAULT 0,
    total_situacao   BIGINT       NOT NULL DEFAULT 0,
    total_endereco   BIGINT       NOT NULL DEFAULT 0,
    total_cnae       BIGINT       NOT NULL DEFAULT 0,
    itens_truncados  BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (nicho_id, referencia)
);
CREATE INDEX ix_resultado_conta ON monitor_resultado (conta_id, id DESC);

CREATE TABLE monitor_resultado_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resultado_id  BIGINT      NOT NULL REFERENCES monitor_resultado (id) ON DELETE CASCADE,
    evento_id     BIGINT      NOT NULL,
    tipo          VARCHAR(20) NOT NULL,
    direcao       VARCHAR(10) NOT NULL,
    cnpj          VARCHAR(14) NOT NULL,
    nome          TEXT,
    descricao     TEXT        NOT NULL
);
CREATE INDEX ix_resultado_item ON monitor_resultado_item (resultado_id, id);

-- Entrega: preferencia, leitura no feed e fila de e-mails (um resumo por usuario e referencia)
CREATE TABLE alerta_preferencia (
    usuario_id     UUID        PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    email_ativo    BOOLEAN     NOT NULL DEFAULT TRUE,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alerta_leitura (
    resultado_id  BIGINT      NOT NULL REFERENCES monitor_resultado (id) ON DELETE CASCADE,
    usuario_id    UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    lido_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (resultado_id, usuario_id)
);

CREATE TABLE alerta_envio (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    conta_id          UUID        NOT NULL REFERENCES conta (id) ON DELETE CASCADE,
    referencia        VARCHAR(7)  NOT NULL,
    status            VARCHAR(12) NOT NULL DEFAULT 'PENDENTE'
                      CHECK (status IN ('PENDENTE', 'ENVIADO', 'FALHOU', 'CANCELADO')),
    tentativas        INTEGER     NOT NULL DEFAULT 0,
    proxima_tentativa TIMESTAMPTZ NOT NULL DEFAULT now(),
    erro              TEXT,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_em        TIMESTAMPTZ,
    UNIQUE (usuario_id, referencia)
);
CREATE INDEX ix_envio_pendente ON alerta_envio (proxima_tentativa) WHERE status = 'PENDENTE';