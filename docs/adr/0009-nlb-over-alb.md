# ADR 0009: Network Load Balancer em vez de Application Load Balancer

**Status**: Aceito
**Data**: 2026-09-13

## Contexto

O Service `workshop-app` (os-service, `k8s/03-app/service.yaml`) precisa expor a App para o [API Gateway](https://github.com/Adriana-Meyer/fiap-tech-challenge-API-gateway-function-serverless) alcançá-la via HTTP proxy. A AWS oferece três modalidades de Elastic Load Balancing: Classic (legado), Application (ALB, camada 7 — roteamento por conteúdo HTTP) e Network (NLB, camada 4 — só encaminha conexões TCP).

Sem uma anotação explícita, o provider de nuvem legado embutido no EKS provisiona por padrão um **Classic Load Balancer** para um Service `type: LoadBalancer` — o que aconteceu de fato numa iteração inicial deste projeto e quebrou o pipeline de validação local (kind), revelando a lacuna.

## Decisão

Usar NLB, forçado via a anotação `service.beta.kubernetes.io/aws-load-balancer-type: "nlb"`.

Motivo de NLB em vez de ALB: a App é um único backend (não há múltiplos serviços que precisem de roteamento por path/host na borda) — o roteamento por conteúdo HTTP já é feito inteiramente pelo API Gateway (`/auth/token` vs `/{proxy+}`). O recurso central do ALB (roteamento L7 para múltiplos target groups) não tem uso nesta arquitetura. NLB entrega menor latência e funciona com uma simples anotação no controller que o EKS já traz — ALB exigiria instalar o AWS Load Balancer Controller (Helm release adicional, com permissões IAM próprias) no [os-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-infrastructure), complexidade sem benefício funcional aqui.

## Consequências

- Se a App evoluir para múltiplos microsserviços dentro do mesmo cluster precisando de roteamento HTTP-aware na borda do cluster (não só no API Gateway), esta decisão precisaria ser revisitada em favor de ALB + Ingress.
- Não é necessário instalar o AWS Load Balancer Controller no [os-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-infrastructure).
