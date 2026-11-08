# Deploy e Infraestrutura

Como o Workshop Management System é conteinerizado, orquestrado e entregue na AWS real — Docker, Kubernetes (EKS), RDS e o pipeline de CI/CD.

A infraestrutura de cluster e banco não vive neste repositório: é provisionada via Terraform nos repositórios [os-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-infrastructure) (VPC + EKS) e [os-database-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-database-infrastructure) (RDS MySQL). Este repositório só aplica os manifests da aplicação contra o cluster já existente.

## Infraestrutura (o que roda no cluster)

Topologia da aplicação depois do `deploy-aws.yml` (namespace, ConfigMap/Secrets, Deployment, HPA e o Service `LoadBalancer`):

```mermaid
flowchart TB
    classDef external fill:#999999,stroke:#6b6b6b,color:#fff
    classDef node fill:#e8e8e8,stroke:#888,color:#000
    classDef deploy fill:#438dd5,stroke:#2e6295,color:#fff
    classDef svc fill:#85bbf0,stroke:#5d82a8,color:#000
    classDef store fill:#ffd580,stroke:#b38600,color:#000

    DockerHub["Docker Hub<br/>[Sistema Externo]<br/>adrianameyer/workshop-management<br/>tags: latest, sha"]:::external
    RDS["RDS MySQL 8.0<br/>[os-database-infrastructure]"]:::external

    subgraph EKS["EKS Cluster 'tech-challenge-eks' (os-infrastructure)"]
      direction TB
      subgraph NS["Namespace: workshop"]
        direction TB

        subgraph Config["ConfigMap / Secrets"]
          direction LR
          CM1["app-config"]:::store
          SEC1["app-secret<br/>JWT_SECRET, WEBHOOK_TOKEN,<br/>SPRING_DATASOURCE_* (aponta pro RDS),<br/>NEW_RELIC_LICENSE_KEY"]:::store
          SEC2["dockerhub-secret<br/>imagePullSecret"]:::store
        end

        DeployApp["Deployment: workshop-app<br/>1 réplica (HPA: 1-5)<br/>requests 250m/256Mi · limits 500m/512Mi<br/>liveness/readiness: /actuator/health/*"]:::deploy
        SvcApp["Service: workshop-app<br/>LoadBalancer (NLB) · 8080"]:::svc
        HPA["HPA: workshop-app-hpa<br/>CPU 70% / Memória 70%<br/>min=1, max=5"]:::deploy
      end
    end

    Host["Acesso externo<br/>hostname do NLB"]:::node

    DockerHub -- "imagePullSecret: dockerhub-secret" --> DeployApp
    CM1 --> DeployApp
    SEC1 --> DeployApp
    DeployApp --> SvcApp
    SvcApp -- "NLB" --> Host
    HPA -. "escala" .-> DeployApp
    DeployApp -- "JDBC :3306" --> RDS
```

> HPA depende de um metrics-server (ou equivalente) disponível no cluster para ler CPU/memória — isso é responsabilidade do os-infrastructure, não deste repositório.

Manifestos em [`k8s/`](../k8s/): `00-namespace/`, `01-config/app-configmap.yaml` e `03-app/` (`deployment.yaml`, `hpa.yaml`, `service.yaml`).

## Pipeline de CI/CD

GitHub Actions ([`.github/workflows/`](../.github/workflows/)):

- **`ci-cd.yml`** — roda em todo push/PR para `develop`/`main`: `build-and-test` (`mvn verify`, gate JaCoCo ≥ 80%) e, só em push (nunca em PR), `build-and-push-image` (build + push da imagem para o Docker Hub: `latest` + `sha`). Não faz nenhum deploy — só valida e publica a imagem.
- **`deploy-aws.yml`** — disparado manualmente (`workflow_dispatch`), com escolha entre `deploy`/`destroy` e ambiente (`homologacao`/`producao`). É o único caminho de deploy do projeto:
  1. `aws eks update-kubeconfig` contra o cluster do os-infrastructure.
  2. Aplica `k8s/00-namespace/namespace.yaml` e `k8s/01-config/app-configmap.yaml`.
  3. Cria/atualiza o Secret `app-secret` a partir de GitHub Secrets — incluindo `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` apontando para o RDS do os-database-infrastructure (chegam automaticamente via `gh secret set`, sem cópia manual) — e o `dockerhub-secret` (imagePullSecret).
  4. Aplica `k8s/03-app/deployment.yaml`, `hpa.yaml` e `service.yaml`.
  5. Espera o rollout, espera o hostname do NLB, faz smoke test em `/actuator/health`.
  - `action: destroy` desfaz na ordem inversa, deletando o Service **primeiro** (libera o NLB) antes do Deployment/HPA/secrets — importante para não deixar o NLB órfão (e cobrando) quando os repositórios os-infrastructure e os-database-infrastructure forem destruídos depois.

Fica `workflow_dispatch` manual permanentemente, inclusive no estado final entregue: essa é a alternativa adotada para economizar os recursos limitados do Lab (sessão de ~4h, cluster/RDS não ficam sempre no ar) — o deploy em si é automático de ponta a ponta assim que disparado, sem nenhuma intervenção manual durante a execução; só o gatilho é manual, para ser acionado quando for conveniente e a sessão do Lab estiver ativa.

> Este projeto já rodou um caminho de deploy local via **kind** (cluster efêmero dentro do próprio runner) durante boa parte do desenvolvimento — validação gratuita e independente de sessão do Lab, enquanto o deploy real na AWS ainda não existia. Com o deploy real testado e validado de ponta a ponta, esse caminho foi removido (ver [ADR 0014](adr/0014-remove-kind-deploy-path.md)); o histórico de como funcionava está em [ADR 0005](adr/0005-terraform-kind-ci-validation.md).

## Segurança e Secrets

- Nenhum valor sensível é commitado. Os secrets do `app-secret` (`JWT_SECRET`, `WEBHOOK_TOKEN`, credenciais do RDS, `NEW_RELIC_LICENSE_KEY`) e do `dockerhub-secret` vêm de GitHub Secrets deste repositório, aplicados via `kubectl create secret ... --dry-run=client -o yaml | kubectl apply -f -` dentro do `deploy-aws.yml`.
- `k8s/secret.yaml.example` é só um template de referência (valores `CHANGE_ME`) — documenta a estrutura esperada, mas não é lido pelo workflow nem aplicado automaticamente.
- As credenciais do RDS (`RDS_DATASOURCE_URL`/`RDS_USERNAME`/`RDS_PASSWORD`) chegam automaticamente como Secrets deste repositório: o `terraform-apply.yml` do os-database-infrastructure faz `terraform output` e envia via `gh secret set` direto para cá (nunca aparecem em log).
- Credenciais AWS (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_SESSION_TOKEN`) são as temporárias da sessão do AWS Academy Learner Lab — precisam ser atualizadas nos GitHub Secrets a cada nova sessão (~4h).
