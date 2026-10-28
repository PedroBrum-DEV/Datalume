-- Dados sinteticos para testar desempenho da busca.
-- Marcados com referencia_alteracao = '0000-00' (remova com limpar.sql). CNPJs "Z..." com DV invalido de proposito.
-- Uso: gerar.ps1 -Quantidade 2000000   (ou psql -v quantidade=2000000 -f gerar.sql)
-- Obs.: com os indices GIN (trigram e secundarios) a insercao de milhoes de linhas leva varios minutos.

INSERT INTO estabelecimento (
    cnpj, cnpj_basico, cnpj_ordem, cnpj_dv, matriz, nome_fantasia, situacao_cadastral, data_situacao,
    data_inicio_atividade, cnae_principal, cnaes_secundarios, logradouro, bairro, cep, uf, municipio_receita,
    municipio_ibge, ddd1, telefone1, email, referencia_alteracao, row_hash)
SELECT
    'Z' || lpad(s.i::text, 7, '0') || '000100',
    'Z' || lpad(s.i::text, 7, '0'),
    '0001', '00', TRUE,
    'FANTASIA ' || upper(substr(md5(s.i::text), 1, 10)),
    CASE WHEN random() < 0.70 THEN '02' WHEN random() < 0.85 THEN '08' WHEN random() < 0.60 THEN '04' ELSE '03' END,
    DATE '2020-01-01' + floor(random() * 2000)::int,
    DATE '1995-01-01' + floor(random() * 11500)::int,
    s.cnae,
    CASE WHEN random() < 0.30 THEN ARRAY[s.cnae2] ELSE NULL END,
    'RUA ' || upper(substr(md5((s.i + 7)::text), 1, 8)),
    'CENTRO',
    lpad(floor(random() * 99999999)::int::text, 8, '0'),
    s.uf,
    '0001',
    CASE s.uf
        WHEN 'SP' THEN 35 WHEN 'RJ' THEN 33 WHEN 'MG' THEN 31 WHEN 'RS' THEN 43 WHEN 'PR' THEN 41
        WHEN 'SC' THEN 42 WHEN 'BA' THEN 29 WHEN 'PE' THEN 26 WHEN 'CE' THEN 23 WHEN 'GO' THEN 52 ELSE 53
    END * 100000 + 5000 + floor(random() * 300)::int,
    '11',
    CASE WHEN random() < 0.70 THEN '9' || lpad(floor(random() * 100000000)::int::text, 8, '0') ELSE NULL END,
    CASE WHEN random() < 0.40 THEN 'c' || substr(md5(s.i::text), 1, 8) || '@exemplo.com.br' ELSE NULL END,
    '0000-00',
    'sintetico'
FROM (
    SELECT i,
           (ARRAY['SP','SP','SP','SP','RJ','RJ','MG','MG','RS','PR','SC','BA','PE','CE','GO','DF'])
               [1 + floor(random() * 16)::int] AS uf,
           (ARRAY['8630504','8630504','4635401','4635402','4635403','4635499','4781400','5611201','5611203',
                  '4711301','4712100','9602501','9602502','8599604','6201501','6202300','4399103','4120400',
                  '4754701','4772500','4744099','4930202','7490104','8211300','9511800','4520001','5620104',
                  '4781400','4399199','8650099'])
               [1 + floor(random() * 30)::int] AS cnae,
           (ARRAY['8630503','8630599','4635499','4781400','5611201','9602501','8599604','6201501'])
               [1 + floor(random() * 8)::int] AS cnae2
    FROM generate_series(1, :quantidade) AS i
) s
ON CONFLICT (cnpj) DO NOTHING;

INSERT INTO empresa (cnpj_basico, razao_social, natureza_juridica, capital_social, porte, referencia_alteracao, row_hash)
SELECT
    'Z' || lpad(i::text, 7, '0'),
    'EMPRESA SINTETICA ' || upper(substr(md5(i::text), 1, 10)),
    '2062',
    floor(random() * 500000)::int,
    (ARRAY['01','01','03','05','00'])[1 + floor(random() * 5)::int],
    '0000-00',
    'sintetico'
FROM generate_series(1, :quantidade) AS i
ON CONFLICT (cnpj_basico) DO NOTHING;

ANALYZE estabelecimento;
ANALYZE empresa;