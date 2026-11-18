# ADR 0007: Uso de roles IAM fixos do AWS Academy (sem IAM próprio)

**Status**: Aceito
**Data**: 2026-09-12

## Contexto

A infraestrutura roda numa conta AWS Academy Learner Lab, que não permite criar roles ou políticas IAM novos (confirmado contra a documentação oficial do Lab) — só service-linked roles automáticos são permitidos. A conta disponibiliza roles fixos e pré-criados: `LabRole` (uso geral — Lambda, API Gateway, RDS, EC2, ELB) e `LabEksClusterRole` (específico para cluster e node group do EKS).

## Decisão

Todo recurso que precisa de uma role IAM referencia esses roles fixos via `data "aws_iam_role"`, em vez de criar (`aws_iam_role`) qualquer role ou política nova:
- [fiap-tech-challenge-API-gateway-function-serverless](https://github.com/Adriana-Meyer/fiap-tech-challenge-API-gateway-function-serverless) (Lambda, API Gateway): `LabRole`
- [os-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-infrastructure) (EKS cluster e node group): `LabEksClusterRole`
- [os-database-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-database-infrastructure) (RDS): `LabRole` (implícito, sem necessidade de role explícita no recurso `aws_db_instance` já que Enhanced Monitoring está desligado)

## Consequências

- Nenhum módulo de Terraform de terceiros que tente criar roles IAM próprios pode ser usado sem adaptação (ex.: módulos populares de EKS na Terraform Registry costumam criar roles automaticamente — teria sido necessário desabilitar essa criação e apontar para os roles do Lab).
- Se o Lab mudar os nomes desses roles entre turmas/sessões, os três repositórios de infra precisam ser atualizados (`LabRole`/`LabEksClusterRole` estão hardcoded como nomes fixos nos `data source`).
- Reflete diretamente uma restrição de ambiente, não uma escolha de design diretamente — documentado aqui para explicar por que o Terraform não segue o padrão mais comum de criar roles dedicados por recurso.
