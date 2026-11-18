# ADR 0005: Terraform + kind para simular deploy em CI sem custo de nuvem

**Status**: Superada por [ADR 0014](0014-remove-kind-deploy-path.md)
**Data**: 2026-07-13

## Contexto

No início, o projeto não tinha nenhum provedor de nuvem real disponível para testar automaticamente o deploy de ponta a ponta — mas precisava provar que a automação (build → imagem → deploy → smoke test) funcionava, sem depender de infraestrutura externa nem gerar custo por execução de pipeline.

## Decisão

Provisionar um cluster **kind** (Kubernetes-in-Docker) via Terraform (`infra/`, provider `tehcyx/kind`), aplicando os manifests reais de `k8s/*` (via `kubernetes_manifest` + `yamldecode(file(...))`, evitando duplicar a mesma definição em dois formatos), dentro do próprio runner do GitHub Actions — criado e destruído a cada execução do pipeline (`ci-cd.yml`/`deploy.yml`).

## Consequências

- Zero custo de nuvem para validar automação de deploy a cada push — só usa o Docker já disponível no runner `ubuntu-latest`.
- O cluster é efêmero: não prova alta disponibilidade real nem performance sob carga, só que o pipeline de ponta a ponta (imagem → manifests → rollout → healthcheck) funciona.
- O provider `kubernetes_manifest` precisa consultar o schema OpenAPI do cluster já no `plan`, criando uma dependência circular na primeira execução (cluster ainda não existe) — resolvida com apply em duas passadas (`-target=kind_cluster.workshop` primeiro, depois o resto).
- Esse caminho foi mantido em paralelo ao deploy real na AWS por um tempo ([ADR 0010](0010-additive-deploy-paths.md)), servindo como validação gratuita de cada push, independente de qualquer sessão do AWS Academy Learner Lab estar ativa. Removido em [ADR 0014](0014-remove-kind-deploy-path.md) depois que o deploy real foi testado de ponta a ponta com sucesso.
