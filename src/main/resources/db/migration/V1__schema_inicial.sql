-- Datalume - schema inicial

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ============================ CONTA ============================
CREATE TABLE plano (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo           VARCHAR(30)  NOT NULL UNIQUE,
    nome             VARCHAR(80)  NOT NULL,
    limite_usuarios  INTEGER      NOT NULL,
    limite_nichos    INTEGER      NOT NULL,
    limite_alertas   INTEGER      NOT NULL,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE conta (
    id             UUID         PRIMARY KEY,
    nome           VARCHAR(150) NOT NULL,
    plano_id       BIGINT       NOT NULL REFERENCES plano (id),
    ativa          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id                 UUID         PRIMARY KEY,
    conta_id           UUID         NOT NULL REFERENCES conta (id),
    nome               VARCHAR(150) NOT NULL,
    email              VARCHAR(254) NOT NULL,
    senha_hash         VARCHAR(100) NOT NULL,
    papel              VARCHAR(20)  NOT NULL CHECK (papel IN ('ADMIN', 'MEMBRO')),
    admin_plataforma   BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo              BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_login_em    TIMESTAMPTZ,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_usuario_email ON usuario (lower(email));
CREATE INDEX ix_usuario_conta ON usuario (conta_id);

-- ========================== REFERÊNCIA ==========================
CREATE TABLE uf (
    codigo_ibge  SMALLINT    PRIMARY KEY,
    sigla        VARCHAR(2)  NOT NULL UNIQUE,
    nome         VARCHAR(40) NOT NULL,
    regiao       VARCHAR(20) NOT NULL
);

CREATE TABLE municipio (
    codigo_ibge       INTEGER      PRIMARY KEY,           -- 7 dígitos
    nome              VARCHAR(120) NOT NULL,
    uf_sigla          VARCHAR(2)   NOT NULL REFERENCES uf (sigla),
    nome_normalizado  VARCHAR(120) NOT NULL               -- MAIÚSCULAS, sem acento/pontuação
);
CREATE INDEX ix_municipio_uf   ON municipio (uf_sigla);
CREATE INDEX ix_municipio_nome ON municipio (nome_normalizado);

-- Conversão: código de município da Receita (4 dígitos, tabela Municipios.csv) -> código IBGE.
-- codigo_ibge fica NULL enquanto não houver correspondência segura.
CREATE TABLE municipio_receita (
    codigo_receita    VARCHAR(4)   PRIMARY KEY,
    nome_receita      VARCHAR(120) NOT NULL,
    nome_normalizado  VARCHAR(120) NOT NULL,
    uf_sigla          VARCHAR(2)   REFERENCES uf (sigla),
    codigo_ibge       INTEGER      REFERENCES municipio (codigo_ibge),
    origem            VARCHAR(20)  NOT NULL CHECK (origem IN ('RECEITA', 'TABELA', 'NOME_UNICO'))
);
CREATE INDEX ix_municipio_receita_ibge ON municipio_receita (codigo_ibge);
CREATE INDEX ix_municipio_receita_nome ON municipio_receita (nome_normalizado);

-- CNAE 2.x, subclasses (7 dígitos). Hierarquia derivada do próprio código.
CREATE TABLE cnae_subclasse (
    codigo     VARCHAR(7)  PRIMARY KEY CHECK (codigo ~ '^[0-9]{7}$'),
    descricao  TEXT        NOT NULL,
    classe     VARCHAR(5)  NOT NULL,
    grupo      VARCHAR(3)  NOT NULL,
    divisao    VARCHAR(2)  NOT NULL,
    secao      VARCHAR(1)
);
CREATE INDEX ix_cnae_descricao_trgm ON cnae_subclasse USING gin (descricao gin_trgm_ops);
