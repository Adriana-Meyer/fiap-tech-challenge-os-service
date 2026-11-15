# Observabilidade (New Relic)

Detalhamento do que o agente New Relic ([README](../README.md#observabilidade-new-relic)) expõe, com prints reais do New Relic como exemplo de cada recurso.

## APM automático

Vem de graça com o agente Java, sem nenhuma instrumentação manual: tempo de resposta, Apdex, throughput e taxa de erro por transação web, além da lista das transações mais lentas.

![Visão geral de APM: tempo de resposta, Apdex, throughput e erros](images/newrelic/apm-overview.png)

![Transações mais lentas e volume de logs](images/newrelic/apm-transactions-logs.png)

## Métricas de infraestrutura do pod

CPU e memória do container `workshop-app`, correlacionadas automaticamente com o pod do Kubernetes pela mesma instrumentação.

![CPU e memória do pod workshop-app](images/newrelic/infrastructure-cpu-memory.png)

## Dashboard customizado "Workshop Management - Fase 3"

Os 4 widgets construídos para os requisitos de observabilidade da Fase 3: volume de transações, tempo médio por status da OS (via o custom event `ServiceOrderStatusDuration`, ver README), latência média das APIs e erros/falhas de integração de webhook.

![Dashboard com os 4 widgets: transações, tempo médio por status, latência e erros](images/newrelic/dashboard-fase3.png)

## Logs estruturados e correlacionados

Logs em JSON (`logstash-logback-encoder`) com `trace.id`/`span.id` injetados automaticamente no MDC — cada linha de log já chega correlacionada com o trace da requisição que a gerou, sem código de correlação manual.

![Logs estruturados em JSON](images/newrelic/logs.png)

## Traces distribuídos

Rastreamento de uma requisição por todas as camadas que ela passa (filtros do Spring Security, chamadas ao banco, código da aplicação), com o tempo gasto em cada segmento.

![Detalhe de um trace: tempo por segmento](images/newrelic/trace-detail-segments.png)

![Detalhe de um trace: árvore completa da transação](images/newrelic/trace-detail-transaction.png)

![Traces agrupados por transação, com contagem, duração e taxa de erro](images/newrelic/traces-overview.png)

## Observabilidade da saga (Fase 4)

Os quatro serviços usam o mesmo agente do New Relic e o mesmo formato de log (JSON, com `trace.id`/`span.id`), cada um com o seu nome de aplicação. O que muda com a saga é que uma operação de negócio passa por quatro serviços e pelo SQS.

- **`sagaId` nos logs:** cada serviço coloca o `sagaId` no MDC ao tratar uma mensagem da saga, e o encoder JSON o expõe como campo de cada linha de log. Para seguir uma saga inteira, filtre pelo campo `sagaId` em todos os serviços de uma vez, por exemplo no New Relic Logs: `sagaId:"<uuid>"`. O `sagaId` de uma OS está em `GET /api/v1/service-orders/{id}/saga`.
- **O trace não atravessa o SQS:** o agente propaga o trace entre chamadas HTTP, mas a mensagem do SQS não carrega o contexto do trace. Por isso o `sagaId` é o elo entre os serviços, e não o `trace.id`. A chamada síncrona do OS ao Inventory (consulta de peça) aparece como segmento externo do trace do OS. Se o trace entre serviços aparece no Lab é uma das verificações pendentes da validação na AWS.
- **Estado da saga:** `saga_instance` guarda o passo atual, o status e o motivo da falha. Uma saga em `COMPENSATING` por muito tempo indica uma confirmação de compensação que não chegou.
- **DLQs:** mensagem que um serviço não consegue tratar vai para a DLQ da fila (`*-commands-dlq`) depois de 3 tentativas. Uma DLQ com mensagens é o sinal de que um serviço está falhando no tratamento (ver [ADR 0020](adr/0020-sqs-sns-messaging.md)).
- **Métricas de status:** o custom event `ServiceOrderStatusDuration` continua sendo publicado pelo OS, e o Execution publica o do status `EXECUTION` ao concluir uma tarefa, para o tempo médio por status do dashboard não perder a etapa de execução.
