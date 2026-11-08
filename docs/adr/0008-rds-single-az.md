# ADR 0008: RDS single-AZ, sem Multi-AZ

**Status**: Aceito
**Data**: 2026-09-13

## Contexto

O RDS ([os-database-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-database-infrastructure)) precisa de uma configuração de disponibilidade. Multi-AZ mantém uma réplica síncrona em standby em outra zona de disponibilidade, promovida automaticamente em caso de falha — mas dobra o custo da instância, e a AWS Academy Learner Lab tem orçamento e sessão (4h) limitados.

## Decisão

A instância RDS é single-AZ (`multi_az = false`). Alta disponibilidade real de banco não é um objetivo desta fase para um projeto acadêmico de demonstração — o requisito obrigatório é "Banco de Dados Gerenciado", não "Banco de Dados Multi-AZ".

## Consequências

- Uma falha na AZ onde a instância roda derruba o banco até a AWS recuperar a instância (sem failover automático) — aceitável dado que o ambiente não fica no ar continuamente (é provisionado/destruído por sessão de estudo).
- Custo por hora aproximadamente metade do que seria com Multi-AZ.
- Caso o projeto evoluísse para um ambiente de produção real, esta decisão precisaria ser revisitada.
