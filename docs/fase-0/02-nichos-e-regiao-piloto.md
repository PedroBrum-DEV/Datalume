# 2. Nichos e região do piloto

## Proposta (aprovar ou trocar)

| Nicho | CNAEs candidatos (subclasse) | Por que serve ao piloto |
|---|---|---|
| Clínicas odontológicas | 8630-5/04 Atividade odontológica | Volume alto, aberturas e baixas frequentes, fornecedores de insumo/equipamento precisam da lista |
| Distribuidoras de bebidas | 4635-4/01 água mineral · 4635-4/02 cerveja, chope e refrigerante · 4635-4/03 bebidas c/ fracionamento · 4635-4/99 bebidas n.e.a. | Venda B2B recorrente; vendedores externos dependem de lista atualizada |
| (opcional) 3º nicho | _a definir_ | |

> Os códigos acima vêm da CNAE 2.x; **confirme as descrições exatas** depois de carregar a base de
> referência: `GET /api/referencia/cnaes?q=odontol` e `GET /api/referencia/cnaes?q=4635` (prefixo do código).
> A API devolve só o que estiver na tabela carregada do IBGE, então ela é a fonte de verdade.

## Região
**Estado de São Paulo (UF = SP)**, com recorte inicial no **município de São Paulo (IBGE 3550308, Receita 7107)**
e depois Grande SP. Região única mantém o piloto pequeno o bastante para validar a qualidade dos dados.

## Critérios de escolha (marque para cada nicho)
| Critério | Odonto | Bebidas | 3º nicho |
|---|---|---|---|
| Tem comprador claro da lista (quem paga?) | | | |
| Mais de 500 estabelecimentos ativos na região | | | |
| CNAE principal descreve bem o nicho (pouco ruído) | | | |
| Giro (aberturas/baixas por mês) gera valor em alertas | | | |
| Você consegue falar com 5+ compradores em 2 semanas | | | |

## Decisão final
- Nichos: _______________________
- Região: _______________________
- Data: ___________
