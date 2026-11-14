# ADR 0021: Infraestrutura como código por serviço (13 repositórios)

**Status**: Aceito
**Data**: 2026-11-14

## Contexto

A Fase 3 já separava o código e a infraestrutura em repositórios independentes: a aplicação, o cluster, o banco e o gateway. Com quatro serviços, a pergunta era se a infraestrutura de todos ficaria num só repositório ou acompanharia a divisão dos serviços.

## Decisão

A infraestrutura acompanha o serviço. Cada serviço tem três repositórios, e há dois compartilhados:

| Repositório | Contém |
|---|---|
| `<serviço>-service` (4) | código, manifests do Kubernetes e o deploy |
| `<serviço>-infrastructure` (3: Billing, Inventory, Execution) | namespace do serviço, ResourceQuota e a fila de comandos com DLQ |
| `<serviço>-database-infrastructure` (4) | o banco do serviço: RDS (OS, Billing, Inventory) ou tabela DynamoDB (Execution) |
| `os-infrastructure` (compartilhado) | VPC, cluster EKS, New Relic, e a **mensageria compartilhada** (tópico `saga-events` e fila `os-saga-events`) |
| API Gateway + Lambda (compartilhado) | entrada única, autenticação por CPF e roteamento por prefixo para os 4 serviços |

Decisões que valem para todos os repositórios Terraform:

- **O cluster é descoberto pelo nome fixo** (`tech-challenge-eks`), por `data source`, sem ler o state de outro repositório: nenhum endereço ou credencial é copiado à mão entre repositórios.
- **Nomes fixos e sem prefixo de projeto** para o que os serviços referenciam por padrão (namespaces e filas).
- **ResourceQuota por namespace**, dimensionada a partir dos manifests reais (pico de 3 pods no rolling update: 750m de CPU e 768Mi de memória pedidos) e com um limite de **um load balancer**: cada serviço tem o seu NLB ([ADR 0009](0009-nlb-over-alb.md)), e um `Service` do tipo `LoadBalancer` esquecido continuaria cobrando depois da sessão do Lab.
- **`apply` manual** (`workflow_dispatch`), pelos mesmos motivos do [ADR 0010](0010-additive-deploy-paths.md) e [ADR 0014](0014-remove-kind-deploy-path.md): poupar o orçamento do Lab. As credenciais do RDS chegam como secrets do repositório do serviço, enviados pelo workflow do repositório de banco.
- **O gateway descobre os hosts dos serviços** num script do workflow (`discover-service-hosts.sh`), que lê o hostname de cada `Service` no cluster; o Terraform só recebe os hosts como variável.
- Os nós do cluster passaram a `t3.large` (de 1 a 3, 2 por padrão): quatro serviços Java com o agente do New Relic não cabem com folga em `t3.medium`.

**Ordem de uso numa sessão do Lab**: `os-infrastructure` → `<serviço>-infrastructure` → `<serviço>-database-infrastructure` → deploy dos serviços → gateway. Para desligar, a ordem inversa (o `destroy` do serviço vem primeiro e libera o NLB).

## Consequências

- **Treze repositórios para manter** e uma ordem de apply que importa; as credenciais do Lab precisam ser renovadas em vários deles a cada sessão (cerca de quatro horas).
- Cada repositório é pequeno e independente, com o seu próprio `terraform-validate.yml`, e os repositórios de serviço seguem implantando sem tocar na infraestrutura.
- Um repositório de infraestrutura por serviço duplica código entre os três (`cluster.tf`, `namespace.tf`, `queue.tf`); o que muda entre eles é só o nome do serviço, o que mantém a duplicação trivial de conferir.
- Custos no Lab: três RDS, quatro load balancers e dois ou três nós `t3.large` ligados ao mesmo tempo. A recomendação é destruir tudo entre as sessões.
