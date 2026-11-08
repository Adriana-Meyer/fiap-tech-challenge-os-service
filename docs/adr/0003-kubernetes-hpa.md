# ADR 0003: Kubernetes com HPA para escalabilidade automática

**Status**: Aceito
**Data**: 2026-07-12

## Contexto

Com a App rodando em contêiner (ver [ADR 0004](0004-docker-multistage-build.md)), era preciso escolher como orquestrá-la e como lidar com variação de carga sem intervenção manual — requisito que antecipa a necessidade de escalabilidade citada já na Fase 2 e formalizada como obrigatória na Fase 3 ("Cluster Kubernetes com escalabilidade").

## Decisão

Kubernetes, com um `HorizontalPodAutoscaler` (`k8s/03-app/hpa.yaml`) escalando o Deployment `workshop-app` entre 1 e 5 réplicas, com base em utilização de CPU e memória (alvo de 70% para ambos), e uma política de scale-up limitada a +1 pod a cada 60s (evita oscilação agressiva). O Deployment declara `requests`/`limits` de CPU e memória (250m/256Mi a 500m/512Mi) — sem eles, o HPA não tem uma base de utilização percentual para calcular contra.

## Consequências

- Exige um metrics-server (ou add-on equivalente) disponível no cluster para o HPA funcionar, expondo a API `metrics.k8s.io` de CPU/memória — responsabilidade do cluster em si (os-infrastructure, no deploy real na AWS), não deste repositório.
- JWT stateless ([ADR 0002](0002-jwt-stateless-auth.md)) é o que torna essa escalabilidade horizontal simples de implementar — qualquer réplica nova atende requisições autenticadas sem coordenação extra.
- O teto de 5 réplicas foi dimensionado para o escopo de demonstração do projeto, não para uma carga de produção real — revisitar se o volume de tráfego mudar de ordem de grandeza.
