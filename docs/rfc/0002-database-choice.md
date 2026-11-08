# RFC 0002: Escolha do banco de dados gerenciado

**Status**: Aceito
**Data**: 2026-09-13

## Resumo

A Fase 3 exige um Banco de Dados Gerenciado (PostgreSQL, MySQL, SQL Server etc., de escolha livre), provisionado como [os-database-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-database-infrastructure), substituindo o MySQL que rodava como pod dentro do cluster nas fases anteriores.

## Contexto

Desde a Fase 1, o projeto usa **MySQL 8**, com o schema versionado via Flyway (4 migrations) e um modelo fortemente relacional (clientes, veículos, ordens de serviço, itens, catálogo, peças, usuários). A Fase 3 pede explicitamente uma "justificativa formal para a escolha do banco de dados" — esta RFC formaliza e expande a justificativa já presente no README desde a Fase 1.

## Opções consideradas

| Opção | Prós | Contras |
|---|---|---|
| **RDS for MySQL** | Mesmo motor já usado e testado desde a Fase 1; zero mudança no schema/Flyway/JPA; suportado nativamente pelo AWS Academy Learner Lab | Nenhum identificado relevante para este escopo |
| Migrar para PostgreSQL (RDS) | Recursos avançados (JSONB, extensões) não utilizados pelo domínio atual | Migração de schema/queries sem benefício concreto — o domínio é puramente relacional simples, sem necessidade dos recursos exclusivos do Postgres |
| Aurora (MySQL-compatible) | Maior disponibilidade/performance para cargas de produção | Custo mais alto, complexidade desnecessária para o volume de um projeto acadêmico de demonstração |

## Decisão

Amazon RDS for MySQL 8.0, mantendo o mesmo motor das fases anteriores.

## Justificativa

Critérios já validados desde a Fase 1 e que continuam se aplicando integralmente:

| Critério | Justificativa |
|---|---|
| **Relacionamentos** | Clientes possuem veículos, veículos possuem OS, OS possuem itens que referenciam serviços e peças — um modelo relacional é o natural para esse domínio. |
| **ACID** | Transações garantem consistência no controle de estoque (decremento na aprovação do orçamento) e nos cálculos financeiros dos totais das OS. |
| **Integridade referencial** | Chaves estrangeiras (`InnoDB`) impedem exclusão de clientes com OS em aberto ou veículos vinculados. |
| **Continuidade** | Trocar de motor agora exigiria reescrever migrations, ajustar dialect do Hibernate e revalidar todo o comportamento já testado — sem ganho correspondente. |
| **Suporte no Lab** | RDS for MySQL está na lista de mecanismos suportados pelo AWS Academy Learner Lab, dentro das classes de instância liberadas (nano/micro/small/medium). |

O modelo relacional completo (diagrama ER e explicação dos relacionamentos) está no [README](../../README.md#modelagem-do-banco-de-dados). O único ajuste de schema feito nesta fase foi `users.email` → `users.cpf` (ver [ADR 0006](../adr/0006-cpf-replaces-email-login.md)) — não há mudança estrutural relacionada à migração para RDS em si, apenas ao local onde o banco roda.

## Consequências

- Ver [ADR 0008](../adr/0008-rds-single-az.md) para a decisão de disponibilidade (single-AZ).
- Nenhuma mudança de código na camada de persistência (`infrastructure/persistence`) foi necessária além da string de conexão.
