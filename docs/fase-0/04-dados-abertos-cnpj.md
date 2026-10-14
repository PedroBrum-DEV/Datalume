# 4. Dados abertos do CNPJ — licença e endereço

Consulta feita em **14/10/2026**. Reconfirme antes de automatizar o download: a Receita já mudou o
endereço mais de uma vez (2020, 2024, 2025).

## Fontes
| O que | Endereço |
|---|---|
| Catálogo oficial (metadados e licença) | https://dados.gov.br/dados/conjuntos-dados/cadastro-nacional-da-pessoa-juridica---cnpj |
| Arquivos mensais (diretório da Receita) | https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/ |
| Compartilhamento alternativo (Nextcloud da Receita) | https://arquivos.receitafederal.gov.br/index.php/s/YggdBLfdninEJX9 |
| Dicionário de dados / layout | https://arquivos.receitafederal.gov.br/dados/cnpj/LAYOUT_DADOS_ABERTOS_CNPJ.pdf |
| Metadados dos arquivos | https://www.gov.br/receitafederal/dados/cnpj-metadados.pdf |

Endereços antigos que ainda aparecem em tutoriais: `dadosabertos.rfb.gov.br/CNPJ/`,
`receita.economia.gov.br/...` e o IP `200.152.38.155`. Não use.

## Licença
O catálogo dados.gov.br informava, nos metadados alterados em 09/02/2026, **Creative Commons Attribution (CC BY)**,
atualização **mensal**. Na prática:
- Uso comercial é permitido; **a atribuição é obrigatória**. Cite a Receita Federal do Brasil como fonte e
  informe a data de referência do dado no produto (sugestão: rodapé "Fonte: Receita Federal do Brasil — CNPJ, dados abertos, ref. AAAA-MM").
- Releia a licença na própria página antes do lançamento e guarde um print com a data.

## Conteúdo (arquivos mensais, ZIP com CSV)
`Empresas0-9`, `Estabelecimentos0-9`, `Socios0-9`, `Simples`, e tabelas: `Cnaes`, `Motivos`, `Municipios`,
`Naturezas`, `Paises`, `Qualificacoes`. CSV com `;` como separador, sem cabeçalho, ISO-8859-1.
O Datalume usa principalmente `Estabelecimentos*`, `Empresas*`, `Cnaes` e `Municipios`.

## Cuidados
- **Código de município**: a Receita usa um código próprio de 4 dígitos, diferente do IBGE.
  O projeto já traz a tabela de conversão (ver `docs/referencia.md`).
- **LGPD**: o cadastro é público, mas contém dados de pessoas físicas (sócios; e-mail/telefone de MEI).
  Decida na Fase 2 o que expor, evite exibir dados pessoais de sócios e tenha um canal de remoção.
- **Volume**: dezenas de milhões de estabelecimentos; planeje disco (dezenas de GB descompactados) e carga em lote.
- Os servidores da Receita costumam ser lentos e instáveis: implemente download com retomada (Fase 2).
- Monitore o `Last-Modified`/nome da pasta do mês para detectar nova versão.
