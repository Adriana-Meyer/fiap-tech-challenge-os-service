# RFC 0001: Escolha da nuvem

**Status**: Aceito
**Data**: 2026-09-12

## Resumo

O projeto exige infraestrutura real em nuvem (API Gateway, Function Serverless, Banco de Dados Gerenciado, Cluster Kubernetes), com escolha livre de provedor.

## Contexto

O projeto é acadêmico, com orçamento e tempo de acesso limitados: a universidade fornece uma conta **AWS Academy Learner Lab**, com sessões de 4 horas, credenciais temporárias, e um saldo fixo de créditos para todo o semestre. Não há orçamento para contratar uma conta AWS/GCP/Azure paga separadamente.

## Opções consideradas

| Opção | Prós | Contras |
|---|---|---|
| **AWS Academy Learner Lab** | Já disponível, sem custo adicional; cobre todos os serviços obrigatórios (API Gateway, Lambda, RDS, EKS) | Sessão de 4h, credenciais temporárias, restrições de IAM (só roles pré-criados), limites de instâncias EC2/vCPU rígidos |
| GCP / Azure com conta paga | Sem as restrições específicas do Academy | Custo direto do bolso, sem crédito educacional disponível para este projeto |
| Multi-cloud (ex.: RDS na AWS, cluster no GCP) | Nenhum benefício real identificado | Complexidade de rede/autenticação entre provedores, sem justificativa técnica para o escopo do projeto |

## Decisão

AWS, via AWS Academy Learner Lab.

## Justificativa

É a única opção sem custo direto disponível, e cobre integralmente os serviços obrigatórios do projeto. As restrições do Lab (IAM fixo, sessão de 4h, limites de instância) são tratadas como restrições de design de primeira classe em todo o Terraform dos repositórios de infraestrutura, não contornadas — ver [ADR 0007](../adr/0007-fixed-iam-roles-aws-academy.md) e os guardrails de conta documentados nos READMEs de infraestrutura.

## Consequências

- Deploy automático contínuo (ex.: push direto para produção a qualquer hora) não é viável enquanto a infraestrutura depender de uma sessão manual do Lab — mitigado mantendo os workflows de `apply` como `workflow_dispatch` manual durante o desenvolvimento.
- Region fixada em `us-east-1` (onde o par de chaves padrão `vockey` do Lab está disponível).
