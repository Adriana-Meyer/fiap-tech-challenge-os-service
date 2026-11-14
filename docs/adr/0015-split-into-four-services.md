# ADR 0015: Divisão do monólito em quatro microsserviços

**Status**: Aceito
**Data**: 2026-11-07

## Contexto

Até a Fase 3 o sistema era um único serviço Spring Boot (o `workshop-management`) que concentrava clientes, veículos, catálogo de serviços, estoque de peças, orçamento, pagamento, execução e a ordem de serviço. A Fase 4 pede microsserviços com banco próprio e uma saga para a operação que atravessa vários domínios (aprovar um orçamento envolve estoque, cobrança e a fila de trabalho dos mecânicos).

Ao mapear o código, os domínios se separavam por quem é dono do dado e das regras: o estoque (`Supply`) só era tocado por estoque; o cálculo do orçamento e a cobrança formam uma unidade; o início e o fim de cada serviço executado, com seus tempos, formam outra.

## Decisão

Quatro serviços, cada um com repositório, banco e infraestrutura próprios:

| Serviço | É dono de | Banco |
|---|---|---|
| **OS** | ordem de serviço, clientes, veículos, catálogo de serviços, usuários e autenticação; **orquestra a saga** | MySQL 8 |
| **Inventory** | peças e insumos, estoque e reservas de estoque por OS | PostgreSQL 16 |
| **Billing** | orçamento e pagamento (o gateway de pagamento é um mock determinístico) | PostgreSQL 16 |
| **Execution** | fila de execução por OS, tarefas, mecânico e tempos; análise de tempo médio por serviço | DynamoDB |

Clientes, veículos e catálogo **ficam no OS**: são dados estáveis, lidos junto com a OS em quase toda operação, e separá-los aumentaria o número de chamadas e de passos da saga sem benefício de independência.

Cada serviço mantém a mesma estrutura em quatro camadas (`domain`, `application`, `infrastructure`, `interfaces`) do [ADR 0001](0001-clean-architecture.md), e um teste ArchUnit falha o build se uma camada violar a regra de dependência.

## Consequências

- **Consistência eventual**: o que antes era uma transação ACID (aprovar o orçamento e baixar o estoque) passa a ser uma saga com compensações ([ADR 0018](0018-orchestrated-saga.md)).
- **Duplicação consciente em vez de biblioteca compartilhada**, na linha do [ADR 0012](0012-duplicated-cpf-validator.md): cada serviço tem a sua classe `Money`, o seu filtro de JWT e o seu envelope de mensagem. Mudar o formato de uma mensagem exige alterar os quatro serviços e os READMEs juntos.
- **Mais coisas para operar**: 4 deployments, 4 load balancers, 3 instâncias RDS e uma tabela DynamoDB, num Learner Lab de orçamento limitado. Os nós do cluster passaram para `t3.large` ([ADR 0021](0021-infrastructure-per-service.md)).
- O monólito deixou de existir: a análise de tempo médio de execução migrou para o Execution (`/api/v1/executions/analytics/...`), porque só ele tem os tempos das tarefas.
