# ADRs (Architecture Decision Records)

Documentação das decisões arquiteturais do projeto, numeradas em ordem cronológica (data em que a decisão foi tomada, não em que o ADR foi escrito).

## Fases 1-2 (registradas retroativamente na Fase 3)

| # | Título |
|---|---|
| [0001](0001-clean-architecture.md) | Clean Architecture com inversão de dependência |
| [0002](0002-jwt-stateless-auth.md) | JWT stateless para autenticação |
| [0003](0003-kubernetes-hpa.md) | Kubernetes com HPA para escalabilidade automática |
| [0004](0004-docker-multistage-build.md) | Docker multi-stage build com usuário non-root |
| [0005](0005-terraform-kind-ci-validation.md) | Terraform + kind para simular deploy em CI sem custo de nuvem — **superada por 0014** |

## Fase 3

| # | Título |
|---|---|
| [0006](0006-cpf-replaces-email-login.md) | CPF substitui e-mail como identificador de login |
| [0007](0007-fixed-iam-roles-aws-academy.md) | Uso de roles IAM fixos do AWS Academy (sem IAM próprio) |
| [0008](0008-rds-single-az.md) | RDS single-AZ, sem Multi-AZ |
| [0009](0009-nlb-over-alb.md) | Network Load Balancer em vez de Application Load Balancer |
| [0010](0010-additive-deploy-paths.md) | Dois caminhos de deploy no os-service, aditivos — **superada por 0014** |
| [0011](0011-java-lambda.md) | Lambda de autenticação em Java, não Node.js |
| [0012](0012-duplicated-cpf-validator.md) | Validação de CPF duplicada, não compartilhada via lib |
| [0013](0013-newrelic-custom-events-for-status-metric.md) | Custom events do New Relic em vez de endpoint REST novo |
| [0014](0014-remove-kind-deploy-path.md) | Remoção do caminho de deploy via kind |
