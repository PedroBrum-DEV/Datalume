-- Remove os dados sinteticos criados por gerar.sql
DELETE FROM estabelecimento WHERE referencia_alteracao = '0000-00';
DELETE FROM empresa WHERE referencia_alteracao = '0000-00';
ANALYZE estabelecimento;
ANALYZE empresa;