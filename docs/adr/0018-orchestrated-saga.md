# ADR 0018: Saga orquestrada pelo OS, com compensações

**Status**: Aceito
**Data**: 2026-11-07

## Contexto

Na Fase 3, aprovar o orçamento baixava o estoque na mesma transação do banco. Com um banco por serviço ([ADR 0016](0016-database-per-service.md)) não há transação que cubra estoque, cobrança e fila de execução. A operação precisa ser uma sequência de passos, cada um com a sua transação local, e um jeito de **desfazer** o que já deu certo quando um passo falha.

## Decisão

**Orquestração**: o OS conduz o fluxo, envia um comando a cada serviço e decide o próximo passo com a resposta. O estado fica na tabela `saga_instance` do OS (passo atual, status, forma de pagamento, motivo da falha, compensações pendentes).

```
Diagnóstico → [1] Billing: GenerateBudget → OS = WAITING_APPROVAL
Aprovação   → [2] Inventory: ReserveStock → [3] Billing: ProcessPayment
            → [4] Execution: EnqueueExecution → OS = IN_EXECUTION
Execução concluída → OS = FINISHED → Inventory: ConsumeStock   (passo pivô, nunca desfeito)
```

**Compensações** (a OS vai a `CANCELLED` e a saga fica `COMPENSATING` até todas as confirmações chegarem):

| Falha em | Desfaz |
|---|---|
| [2] estoque insuficiente | `CancelBudget` |
| [3] pagamento recusado | `ReleaseStock` e `CancelBudget` |
| [4] fila de execução | `RefundPayment` (que já cancela o orçamento) e `ReleaseStock` |

Rejeitar o orçamento encerra a saga como `REJECTED` e devolve a OS a `IN_DIAGNOSIS`; um novo diagnóstico abre uma saga nova.

**Garantias**:
- Cada mensagem tem um `messageId`, e o OS registra os já tratados (`processed_message`): uma entrega repetida é ignorada.
- Uma resposta que não corresponde ao passo em que a saga está (duplicata tardia, mensagem perdida no tempo) é descartada com um log.
- Cada serviço deriva o `messageId` da resposta do `messageId` do comando, então uma reentrega do comando gera a mesma resposta e é descartada pelo OS.
- Duas confirmações de compensação podem chegar juntas, de serviços diferentes: a linha da saga é lida com lock pessimista, senão a última gravação apagava a outra e a saga ficava em `COMPENSATING` para sempre (há um teste de concorrência que falha sem o lock).
- Recusa de pagamento, falta de estoque e ordem inválida são **respostas**, não erros: o que o serviço não consegue tratar vai para a DLQ ([ADR 0020](0020-sqs-sns-messaging.md)).

## Alternativas

- **Coreografia** (cada serviço reage ao evento do anterior): sem ponto central, mas o fluxo e as compensações ficariam espalhados em quatro bases de código, difíceis de ler e de depurar. Com a orquestração, `GET /service-orders/{id}/saga` mostra onde a saga está.

## Consequências

- **O OS conhece o fluxo inteiro**; mudar a ordem dos passos mexe no OS.
- **A OS fica em `WAITING_APPROVAL` durante a saga de aprovação** e só vira `IN_EXECUTION` quando o Execution confirma; o progresso aparece no endpoint da saga, sem criar status intermediários. O status `CANCELLED` é novo.
- **Sem outbox transacional**: o comando é publicado antes do commit. Se o commit falha depois do envio, o comando já saiu; os receptores são idempotentes, então o reenvio é seguro. O outbox fica registrado como melhoria.
- O diagnóstico passou a exigir ao menos um serviço na OS: o Billing exige uma linha de orçamento e o Execution, uma tarefa.
- O `CancelBudget` também roda no caso de estoque insuficiente, para não deixar um orçamento vivo de uma OS cancelada.
