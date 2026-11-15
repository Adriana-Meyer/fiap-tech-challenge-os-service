# ADR 0020: Mensageria com SQS (comandos) e SNS (respostas)

**Status**: Aceito
**Data**: 2026-11-11

## Contexto

A saga ([ADR 0018](0018-orchestrated-saga.md)) precisa que o OS mande comandos a três serviços e receba as respostas deles, sem acoplar um ao tempo de resposta do outro. O ambiente é o AWS Academy Learner Lab, que oferece SQS e SNS gerenciados.

## Decisão

```
OS ──comandos──▶ billing-commands, inventory-commands, execution-commands (SQS, uma por serviço, cada uma com DLQ)

Billing / Inventory / Execution ──respostas──▶ tópico SNS saga-events ──▶ fila os-saga-events (SQS) ──▶ OS
```

- **Comandos por SQS ponto a ponto**, uma fila por serviço: só o dono da fila a lê. Cada fila tem a sua DLQ e move a mensagem para ela depois de **3 tentativas**.
- **Respostas por um tópico SNS** que alimenta a fila do OS, com **entrega bruta** (`RawMessageDelivery`): o corpo da fila é o envelope, sem o invólucro do SNS. Um novo consumidor das respostas (por exemplo, um serviço de notificação) pode assinar o tópico sem mudar nenhum dos serviços atuais.
- **Envelope único**, nos dois sentidos:

  ```json
  { "messageId": "…", "sagaId": "…", "serviceOrderId": "…", "type": "GenerateBudget", "payload": { … } }
  ```

  `type` nomeia o comando ou o evento, e o `payload` leva só os campos próprios dele. O catálogo de mensagens está no [README do OS](../../README.md#mensageria-da-saga).
- **Os nomes são fixos** (`billing-commands`, `saga-events`…): os serviços usam esses nomes por padrão e montam o ARN do tópico a partir de conta, região e nome. As filas e o tópico **pertencem ao Terraform**: um serviço que não encontra a sua fila falha na subida, em vez de criá-la sem DLQ (a biblioteca criaria sozinha por padrão).
- **Entrega "pelo menos uma vez" com idempotência**: o `messageId` da resposta é derivado do `messageId` do comando (e o `ExecutionCompleted` tem um id por saga), então uma reentrega gera a mesma resposta e o OS a descarta.
- **Falha não confirma a mensagem**: JSON inválido, tipo desconhecido ou um tratamento que lança exceção deixam a mensagem voltar à fila e, no fim, ir para a DLQ. Respostas de negócio (pagamento recusado, estoque insuficiente) não são falhas: são eventos.
- **Publicação pelo SDK da AWS**, e o Spring Cloud AWS só para escutar as filas: o corpo JSON sai exatamente como o envelope, sem a conversão implícita de payloads.
- O `sagaId` entra no MDC de cada tratamento, e portanto em todo log JSON, para seguir uma saga pelos quatro serviços mesmo que o trace do New Relic não atravesse o SQS.

## Alternativas

- **Kafka ou RabbitMQ**: exigiriam operar um broker no cluster, o que pesa num Lab de orçamento limitado, quando SQS e SNS já são gerenciados.
- **Filas FIFO**: a ordem só importa dentro de uma saga, e ali cada passo envia o comando seguinte só depois da resposta do anterior; a idempotência já cobre a entrega duplicada.

## Consequências

- **Sem outbox transacional**: a resposta é publicada depois de gravar, e o comando do OS antes do commit. Uma falha no meio é refeita pela reentrega e absorvida pela idempotência ([ADR 0018](0018-orchestrated-saga.md)).
- Os pods acessam SQS, SNS e DynamoDB pelo role IAM do nó (`LabEksNodeRole`), que o Lab não deixa alterar. Se esse role não permitir essas ações, o plano alternativo é fornecer as credenciais da sessão por um Secret; isso é verificado na validação no Lab.
- O contrato é testado de ponta a ponta no ambiente local (`e2e/`, com LocalStack), com os quatro serviços reais.
