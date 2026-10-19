-- Estabelecimentos, empresas e controle de importação

-- Converte datas AAAAMMDD da Receita; '0', '00000000' e valores inválidos viram NULL.
CREATE FUNCTION parse_data_rfb(t TEXT) RETURNS DATE
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
BEGIN
    IF t IS NULL OR t !~ '^[0-9]{8}$' OR t = '00000000' THEN
        RETURN NULL;
    END IF;
    RETURN to_date(t, 'YYYYMMDD');
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END
$$;

-- Índice auxiliar para resolver município da Receita -> IBGE por nome + UF
CREATE INDEX ix_municipio_nome_uf ON municipio (nome_normalizado, uf_sigla);

CREATE TABLE natureza_juridica (
    codigo     VARCHAR(4) PRIMARY KEY,
    descricao  TEXT       NOT NULL
);

CREATE TABLE motivo_situacao (
    codigo     VARCHAR(2) PRIMARY KEY,
    descricao  TEXT       NOT NULL
);

-- CNPJ básico (8 caracteres) pode ser alfanumérico a partir de 2026: por isso VARCHAR e não número.
CREATE TABLE empresa (
    cnpj_basico               VARCHAR(8)    PRIMARY KEY,
    razao_social              TEXT          NOT NULL,
    natureza_juridica         VARCHAR(10),
    qualificacao_responsavel  VARCHAR(10),
    capital_social            NUMERIC(20,2),
    porte                     VARCHAR(2),
    ente_federativo           TEXT,
    referencia_alteracao      VARCHAR(7)    NOT NULL,
    row_hash                  VARCHAR(32)   NOT NULL,
    criado_em                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE estabelecimento (
    cnpj                   VARCHAR(14)  PRIMARY KEY,
    cnpj_basico            VARCHAR(8)   NOT NULL,
    cnpj_ordem             VARCHAR(4)   NOT NULL,
    cnpj_dv                VARCHAR(2)   NOT NULL,
    matriz                 BOOLEAN      NOT NULL,
    nome_fantasia          TEXT,
    situacao_cadastral     VARCHAR(2),
    data_situacao          DATE,
    motivo_situacao        VARCHAR(2),
    cidade_exterior        TEXT,
    pais                   VARCHAR(10),
    data_inicio_atividade  DATE,
    cnae_principal         VARCHAR(7),
    cnaes_secundarios      TEXT[],
    tipo_logradouro        TEXT,
    logradouro             TEXT,
    numero                 TEXT,
    complemento            TEXT,
    bairro                 TEXT,
    cep                    VARCHAR(20),
    uf                     VARCHAR(2),
    municipio_receita      VARCHAR(4),
    municipio_ibge         INTEGER,
    ddd1                   VARCHAR(10),
    telefone1              VARCHAR(20),
    ddd2                   VARCHAR(10),
    telefone2              VARCHAR(20),
    email                  TEXT,
    referencia_alteracao   VARCHAR(7)   NOT NULL,   -- referência (AAAA-MM) da última alteração
    row_hash               VARCHAR(32)  NOT NULL,   -- md5 dos dados; evita regravar linhas iguais
    criado_em              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_estab_basico    ON estabelecimento (cnpj_basico);
CREATE INDEX ix_estab_cnae_uf   ON estabelecimento (cnae_principal, uf, municipio_ibge);
CREATE INDEX ix_estab_municipio ON estabelecimento (municipio_ibge, cnae_principal);

-- Áreas de passagem (staging): recebem o CSV bruto via COPY. UNLOGGED = carga mais rápida.
CREATE UNLOGGED TABLE stg_estabelecimento (
    c01 TEXT,
    c02 TEXT,
    c03 TEXT,
    c04 TEXT,
    c05 TEXT,
    c06 TEXT,
    c07 TEXT,
    c08 TEXT,
    c09 TEXT,
    c10 TEXT,
    c11 TEXT,
    c12 TEXT,
    c13 TEXT,
    c14 TEXT,
    c15 TEXT,
    c16 TEXT,
    c17 TEXT,
    c18 TEXT,
    c19 TEXT,
    c20 TEXT,
    c21 TEXT,
    c22 TEXT,
    c23 TEXT,
    c24 TEXT,
    c25 TEXT,
    c26 TEXT,
    c27 TEXT,
    c28 TEXT,
    c29 TEXT,
    c30 TEXT
);
CREATE UNLOGGED TABLE stg_empresa (
    c01 TEXT,
    c02 TEXT,
    c03 TEXT,
    c04 TEXT,
    c05 TEXT,
    c06 TEXT,
    c07 TEXT
);
CREATE UNLOGGED TABLE stg_codigo (
    c01 TEXT,
    c02 TEXT
);

-- Controle de importação
CREATE TABLE importacao_lote (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    referencia     VARCHAR(7)   NOT NULL,
    status         VARCHAR(12)  NOT NULL CHECK (status IN ('EXECUTANDO', 'CONCLUIDO', 'FALHOU')),
    filtro         TEXT,
    erro           TEXT,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finalizado_em  TIMESTAMPTZ
);
CREATE INDEX ix_importacao_lote_ref ON importacao_lote (referencia, id DESC);
-- Só uma importação pode estar EXECUTANDO por vez.
CREATE UNIQUE INDEX ux_importacao_lote_executando ON importacao_lote ((TRUE)) WHERE status = 'EXECUTANDO';

CREATE TABLE importacao_arquivo (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lote_id        BIGINT       NOT NULL REFERENCES importacao_lote (id) ON DELETE CASCADE,
    nome           VARCHAR(80)  NOT NULL,
    tipo           VARCHAR(20)  NOT NULL,
    ordem          INTEGER      NOT NULL,
    status         VARCHAR(12)  NOT NULL DEFAULT 'PENDENTE'
                   CHECK (status IN ('PENDENTE', 'BAIXANDO', 'IMPORTANDO', 'CONCLUIDO', 'FALHOU')),
    tamanho_bytes  BIGINT,
    linhas_lidas   BIGINT,
    inseridos      BIGINT,
    atualizados    BIGINT,
    tentativas     INTEGER      NOT NULL DEFAULT 0,
    erro           TEXT,
    iniciado_em    TIMESTAMPTZ,
    finalizado_em  TIMESTAMPTZ,
    UNIQUE (lote_id, nome)
);