# Ambiente local ponta a ponta

Sobe o sistema inteiro da Fase 4 na sua máquina e verifica a saga de verdade: os **4 serviços reais** (OS, Billing, Inventory e Execution), cada um com o seu banco, conversando por **SQS e SNS** (LocalStack). É o único lugar onde o contrato entre os serviços é testado com todos juntos.

| Componente | Imagem / origem | Porta local |
|---|---|---|
| OS (orquestrador) | este repositório | 8080 |
| Inventory | `../fiap-tech-challenge-inventory-service` | 8081 |
| Billing | `../fiap-tech-challenge-billing-service` | 8082 |
| Execution | `../fiap-tech-challenge-execution-service` | 8083 |
| OS DB / Billing DB / Inventory DB | MySQL 8.0 / PostgreSQL 16 / PostgreSQL 16 | (internos) |
| SQS + SNS + DynamoDB | LocalStack 3.8.1 | 4566 |

O LocalStack já nasce com a topologia que o Terraform criará na AWS (`localstack-init/01-saga-topology.sh`): as filas `billing-commands`, `inventory-commands`, `execution-commands` e `os-saga-events`, cada uma com DLQ (`<fila>-dlq`, 3 tentativas), o tópico `saga-events` e a assinatura da fila do OS com entrega bruta. A tabela do Execution é criada pelo próprio serviço (`DYNAMODB_AUTO_CREATE_TABLE`).

## Como rodar

```bash
# na raiz deste repositório; os repositórios dos serviços devem estar ao lado dele
docker compose -f e2e/docker-compose.yml up -d --build
bash e2e/run-e2e.sh
docker compose -f e2e/docker-compose.yml down -v      # desliga e apaga os dados
```

Se os repositórios estiverem em outro lugar: `SERVICES_DIR=/caminho/dos/repos docker compose -f e2e/docker-compose.yml up -d --build`. Precisa de `curl`, `jq` e Docker.

> O script de inicialização do LocalStack é copiado para a imagem (`localstack.Dockerfile`) em vez de montado como volume: o Docker Desktop no macOS não monta pastas dentro de `~/Desktop` sem permissão extra.

## O que o `run-e2e.sh` verifica

Cada cenário abre uma OS nova (o cliente e o veículo são reutilizados, então o script pode rodar várias vezes) e confere o estado final **em cada serviço**, pela API dele:

1. **Fluxo feliz** (troca de óleo + 2 filtros, cartão): orçamento calculado pelo Billing (total 200), aprovação, estoque reservado no Inventory (2 filtros a menos), pagamento aprovado (orçamento `PAID`), fila de execução no Execution, mecânico inicia e conclui a tarefa, OS `FINISHED`, saga `COMPLETED`, reserva `CONSUMED` e entrega.
2. **Pagamento recusado** (`mock-decline`): OS `CANCELLED`, saga `COMPENSATED`, estoque devolvido, reserva `RELEASED`, pagamento `DECLINED`, orçamento `CANCELLED` e o motivo guardado na saga.
3. **Estoque insuficiente** (11 discos de freio para um estoque de 10): OS `CANCELLED`, saga `COMPENSATED`, nada foi retirado do estoque, nenhuma reserva nem cobrança, orçamento `CANCELLED`.

No fim, confere que nenhuma mensagem foi parar em uma DLQ. O código de saída é 0 só se tudo passar.

## Para investigar

```bash
docker compose -f e2e/docker-compose.yml logs -f os billing inventory execution
curl -s localhost:8080/api/v1/service-orders/<id>/saga -H "Authorization: Bearer <token>" | jq
docker exec e2e-localstack awslocal sqs list-queues
```

Os logs são JSON e trazem o `sagaId` em toda linha de tratamento de mensagem: filtre por ele para seguir uma saga pelos quatro serviços.
