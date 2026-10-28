-- Consultas tipicas com EXPLAIN. Rode apos gerar.sql e confira o uso dos indices:
--   Get-Content scripts\sinteticos\explain.sql | docker exec -i datalume-db psql -U datalume -d datalume
-- Esperado: Index Scan / Bitmap Index Scan (ix_estab_ativas_cnae, ix_estab_cnae_uf, ix_estab_abertura,
-- ix_estab_secundarios, ix_estab_fantasia_trgm). Seq Scan em tabela grande = investigar.

-- 1) Nicho tipico: odontologia em SP, ativas, primeira pagina (abertura mais recente)
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.cnpj FROM estabelecimento e
WHERE e.cnae_principal IN ('8630504') AND e.uf IN ('SP') AND e.situacao_cadastral IN ('02')
ORDER BY COALESCE(e.data_inicio_atividade, DATE '1900-01-01') DESC, e.cnpj DESC
LIMIT 51;

-- 2) Contagem limitada (10.001) do mesmo recorte
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM (
    SELECT 1 FROM estabelecimento e
    WHERE e.cnae_principal IN ('8630504') AND e.uf IN ('SP') AND e.situacao_cadastral IN ('02')
    LIMIT 10001) t;

-- 3) Inclui CNAE secundario (GIN)
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.cnpj FROM estabelecimento e
WHERE (e.cnae_principal IN ('8630504') OR e.cnaes_secundarios && CAST('{8630504}' AS text[]))
  AND e.uf IN ('SP') AND e.situacao_cadastral IN ('02')
ORDER BY COALESCE(e.data_inicio_atividade, DATE '1900-01-01') DESC, e.cnpj DESC
LIMIT 51;

-- 4) Busca por nome (trigram)
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.cnpj FROM estabelecimento e
WHERE e.nome_fantasia ILIKE '%FANTASIA 1A%' AND e.uf IN ('SP')
LIMIT 51;