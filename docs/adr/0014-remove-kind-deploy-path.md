# ADR 0014: Remoção do caminho de deploy via kind

**Status**: Aceito
**Data**: 2026-09-15

## Contexto

O caminho de deploy via **kind** ([ADR 0005](0005-terraform-kind-ci-validation.md)) foi mantido em paralelo ao deploy real na AWS durante a Fase 3 ([ADR 0010](0010-additive-deploy-paths.md)), como validação gratuita e independente de sessão do AWS Academy Learner Lab a cada push. Em 2026-09-15, o deploy real (`deploy-aws.yml`) foi testado com sucesso de ponta a ponta contra o EKS ([os-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-infrastructure)) e o RDS ([os-database-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-database-infrastructure)): CPF → API Gateway → Lambda → App → RDS → JWT, incluindo uma rota protegida e um ciclo completo de ordem de serviço.

Com a evolução do projeto para a nuvem confirmada, manter dois caminhos de deploy deixou de compensar: gera duplicação de manifests (`k8s/03-app/service.yaml` NodePort, do kind, vs. `k8s/aws/service-loadbalancer.yaml` LoadBalancer, da AWS — pasta separada só porque o Terraform do kind aplicava genericamente todo `.yaml` de `03-app/`), duplicação de Terraform (`infra/` local vs. os repositórios os-infrastructure e os-database-infrastructure reais) e um `ci-cd.yml` que descreve um comportamento de "produção" (`deploy-hml`/`deploy-prod`) que não reflete o deploy real do projeto.

## Decisão

Remover o caminho kind por completo:
- **`infra/`** (Terraform do kind: `cluster.tf`, `main.tf`, `manifests.tf`, `secrets.tf`, `variables.tf`, `metrics-server.tf` + manifests vendorizados do metrics-server).
- **`k8s/02-mysql/*`**, **`k8s/01-config/mysql-configmap.yaml`**, **o `service.yaml` (NodePort) do kind** — o banco agora é sempre o RDS do os-database-infrastructure, e o Service exposto é sempre o `LoadBalancer` que antes vivia em `k8s/aws/`.
- A pasta **`k8s/aws/`** deixa de existir: sem o glob do Terraform do kind, não há mais motivo para separar os manifests da AWS dos demais. `service-loadbalancer.yaml` → `k8s/03-app/service.yaml`, `secret-aws.yaml.example` → `k8s/secret.yaml.example` (raiz, junto dos outros templates de referência).
- **`k8s/secret.yaml.example`** e **`k8s/mysql-secret.yaml.example`** — templates que só faziam sentido enquanto `infra/secrets.tf` criava esses Secrets via `TF_VAR_*`.
- **`.github/workflows/deploy.yml`** (workflow reusável do kind) e os jobs `deploy-hml`/`deploy-prod` de `.github/workflows/ci-cd.yml`.
- Entradas de `.gitignore` específicas de `infra/`.

`ci-cd.yml` passa a ter só `build-and-test` (gate JaCoCo) e `build-and-push-image` — nenhum job de deploy. O único caminho de deploy do projeto passa a ser `deploy-aws.yml`, `workflow_dispatch` manual permanentemente (ver [ADR 0010](0010-additive-deploy-paths.md)).

## Consequências

- **Perda da rede de segurança gratuita por push**: antes, todo push validava de ponta a ponta (subir cluster → aplicar manifests → rollout → smoke test) sem depender de nenhuma sessão AWS. Depois desta mudança, `ci-cd.yml` só roda `mvn verify` — a única forma de validar que os manifests do K8s realmente sobem é disparando `deploy-aws.yml` manualmente contra o Lab, o que exige sessão ativa e credenciais atualizadas. Aceito conscientemente: o projeto já não depende mais de provar automação sem nuvem, e a Fase 3 avalia o deploy real, não o kind.
- Elimina a duplicação entre os dois conjuntos de manifests/Terraform — só existe uma definição de "como a app é deployada" a partir de agora.
- `k8s/` fica mais simples e achatado: `00-namespace/`, `01-config/app-configmap.yaml`, `03-app/` (`deployment.yaml`, `hpa.yaml`, `service.yaml`) e `secret.yaml.example` na raiz — sem pasta por ambiente, já que só existe um.
- [ADR 0005](0005-terraform-kind-ci-validation.md) e [ADR 0010](0010-additive-deploy-paths.md) permanecem como registro histórico da decisão original, marcadas como superadas por esta ADR.
