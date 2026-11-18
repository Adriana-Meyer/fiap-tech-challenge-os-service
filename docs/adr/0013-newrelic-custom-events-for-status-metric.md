# ADR 0013: Custom events do New Relic em vez de endpoint REST novo para a métrica de status

**Status**: Aceito
**Data**: 2026-09-13

## Contexto

O dashboard exige "tempo médio de execução por status (Diagnóstico, Execução, Finalização)". Já existia um endpoint (`GET /api/v1/service-orders/analytics/avg-execution-time`) calculando tempo médio, mas **por tipo de serviço** (ex.: troca de óleo), não por status da OS — não atenderia ao requisito.

## Decisão

Em vez de criar um novo endpoint REST agregando os timestamps de status (`diagnosis_started_at`, `waiting_approval_at`, `execution_started_at`, `execution_finished_at`, `delivered_at`, já existentes no schema), a métrica é publicada via custom event do New Relic (`ServiceOrderStatusDuration`) diretamente nos pontos de transição de status (`CompleteDiagnosisUseCase`, `FinishServiceItemExecutionUseCase`, `DeliverServiceOrderUseCase`), através de uma porta `MetricsPublisher` implementada por `NewRelicMetricsPublisher`.

Motivo: essa é a forma nativa de alimentar um dashboard do New Relic (consultado via NRQL) — não exige criar, testar e documentar uma nova rota HTTP, um novo DTO e uma nova query de agregação só para servir um dashboard externo à própria API.

## Consequências

- A métrica só existe dentro do New Relic (via NRQL `SELECT average(durationMinutes) FROM ServiceOrderStatusDuration FACET status`) — não é consultável via API própria da aplicação. Se a métrica precisasse ser exposta programaticamente para outro consumidor além do dashboard, um endpoint dedicado ainda seria necessário.
- Acoplamento pequeno e explícito das três use cases ao `MetricsPublisher` (porta de domínio, não ao SDK do New Relic diretamente) — segue o mesmo padrão porta/adapter já usado para notificações, preservando a Clean Architecture.
