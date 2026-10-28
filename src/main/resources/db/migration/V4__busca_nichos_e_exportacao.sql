-- Indices de busca (V3 ja criou ix_estab_cnae_uf e ix_estab_municipio)
CREATE INDEX ix_estab_ativas_cnae ON estabelecimento (cnae_principal, municipio_ibge)
    WHERE situacao_cadastral = '02';
CREATE INDEX ix_estab_secundarios ON estabelecimento USING gin (cnaes_secundarios);
-- Ordenacao padrao da busca: abertura mais recente primeiro, cnpj como desempate (mesma expressao da query)
CREATE INDEX ix_estab_abertura ON estabelecimento ((COALESCE(data_inicio_atividade, DATE '1900-01-01')) DESC, cnpj DESC);
CREATE INDEX ix_estab_fantasia_trgm ON estabelecimento USING gin (nome_fantasia gin_trgm_ops);
CREATE INDEX ix_empresa_razao_trgm ON empresa USING gin (razao_social gin_trgm_ops);

-- Limite de linhas por exportacao, por plano
ALTER TABLE plano ADD COLUMN limite_exportacao INTEGER NOT NULL DEFAULT 100;
UPDATE plano SET limite_exportacao = 100   WHERE codigo = 'GRATUITO';
UPDATE plano SET limite_exportacao = 5000  WHERE codigo = 'PILOTO';
UPDATE plano SET limite_exportacao = 50000 WHERE codigo = 'PROFISSIONAL';

-- Nichos salvos por conta (recorte: CNAEs + regiao). Base do monitor.
CREATE TABLE nicho (
    id                   UUID         PRIMARY KEY,
    conta_id             UUID         NOT NULL REFERENCES conta (id) ON DELETE CASCADE,
    nome                 VARCHAR(100) NOT NULL,
    cnaes                TEXT[]       NOT NULL,
    incluir_secundarios  BOOLEAN      NOT NULL DEFAULT FALSE,
    ufs                  TEXT[]       NOT NULL DEFAULT '{}',
    municipios           INTEGER[]    NOT NULL DEFAULT '{}',
    criado_em            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_nicho_conta_nome ON nicho (conta_id, lower(nome));
CREATE INDEX ix_nicho_conta ON nicho (conta_id);

-- Registro de quem exportou o que (rastro para LGPD). Sem FK no nicho: sobrevive a exclusao.
CREATE TABLE exportacao (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conta_id    UUID         NOT NULL REFERENCES conta (id),
    usuario_id  UUID         NOT NULL REFERENCES usuario (id),
    nicho_id    UUID,
    nicho_nome  VARCHAR(100),
    criterios   TEXT         NOT NULL,
    contatos    BOOLEAN      NOT NULL,
    linhas      INTEGER      NOT NULL,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_exportacao_conta ON exportacao (conta_id, id DESC);