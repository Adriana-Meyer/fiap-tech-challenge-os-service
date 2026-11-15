# Arquitetura

Documentação de arquitetura do Workshop Management System, usando o modelo **C4** (Contexto → Container → Componente) para descrever o sistema em níveis crescentes de detalhe.

O sistema deixou de ser um monólito e hoje são **quatro microsserviços** (OS, Inventory, Billing e Execution), cada um com banco próprio, que cooperam por uma **saga orquestrada** sobre SQS e SNS. Este documento descreve o estado atual. Para o modelo de dados do OS (diagrama entidade-relacionamento), ver a seção [Modelagem do Banco de Dados](../README.md#modelagem-do-banco-de-dados) no README; os demais serviços descrevem o seu modelo nos próprios READMEs. As decisões estão registradas como [ADRs](adr/) (os dos microsserviços e da saga são do [0015](adr/0015-split-into-four-services.md) ao [0021](adr/0021-infrastructure-per-service.md)) e [RFCs](rfc/).

## Visão de Nuvem

Como as peças se conectam em produção: o API Gateway é a entrada única e roteia cada prefixo da API para o serviço dono dele; os serviços conversam por filas e por um tópico; cada um tem o seu banco.

```mermaid
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef gw fill:#ffd580,stroke:#b38600,color:#000
    classDef svc fill:#438dd5,stroke:#2e6295,color:#fff
    classDef db fill:#f4a6a6,stroke:#a85252,color:#000
    classDef msg fill:#b8e0b8,stroke:#4a8f4a,color:#000
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff

    Funcionario["Funcionário da Oficina<br/>[Pessoa]"]:::person

    subgraph GW["API Gateway + Lambda"]
        direction TB
        APIGW["API Gateway (HTTP API)<br/>roteia por prefixo"]:::gw
        Lambda["Lambda auth-cpf (Java 17)<br/>valida CPF, delega login ao OS"]:::gw
    end

    subgraph EKS["EKS tech-challenge-eks (os-infrastructure)"]
        direction TB
        OS["OS Service<br/>namespace workshop<br/>orquestra a saga"]:::svc
        INV["Inventory Service<br/>namespace inventory"]:::svc
        BIL["Billing Service<br/>namespace billing"]:::svc
        EXE["Execution Service<br/>namespace execution"]:::svc
    end

    subgraph DATA["Um banco por serviço"]
        direction TB
        DBOS[("RDS MySQL 8")]:::db
        DBINV[("RDS PostgreSQL 16")]:::db
        DBBIL[("RDS PostgreSQL 16")]:::db
        DBEXE[("DynamoDB<br/>execution-orders")]:::db
    end

    subgraph MSG["Mensageria"]
        direction TB
        CMD["SQS de comandos (com DLQ)<br/>billing-commands<br/>inventory-commands<br/>execution-commands"]:::msg
        EVT["SNS saga-events<br/>→ SQS os-saga-events"]:::msg
    end

    NewRelic["New Relic [SaaS]<br/>APM, logs, métricas, dashboards"]:::ext

    Funcionario -- "POST /auth/token" --> APIGW
    APIGW -- "invoke" --> Lambda
    Lambda -- "POST /api/v1/auth/login" --> OS
    Funcionario -- "/api/v1/* + Bearer JWT" --> APIGW
    APIGW -- "demais rotas" --> OS
    APIGW -- "/supplies, /stock-reservations" --> INV
    APIGW -- "/budgets, /payments" --> BIL
    APIGW -- "/executions" --> EXE

    OS -- "JDBC" --> DBOS
    INV -- "JDBC" --> DBINV
    BIL -- "JDBC" --> DBBIL
    EXE -- "SDK" --> DBEXE

    OS -- "REST: consulta de peça" --> INV
    OS -- "comandos" --> CMD
    CMD -- "um consumidor por fila" --> INV
    CMD --> BIL
    CMD --> EXE
    INV -- "respostas" --> EVT
    BIL --> EVT
    EXE --> EVT
    EVT -- "respostas" --> OS

    EKS -. "agente Java e nri-bundle" .-> NewRelic
```

Repositórios: [os-service](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-service) (este) · [inventory-service](https://github.com/Adriana-Meyer/fiap-tech-challenge-inventory-service) · [billing-service](https://github.com/Adriana-Meyer/fiap-tech-challenge-billing-service) · [execution-service](https://github.com/Adriana-Meyer/fiap-tech-challenge-execution-service) · [API Gateway + Lambda](https://github.com/Adriana-Meyer/fiap-tech-challenge-API-gateway-function-serverless) · [os-infrastructure](https://github.com/Adriana-Meyer/fiap-tech-challenge-os-infrastructure) (VPC, EKS, New Relic, tópico `saga-events`) · e, para cada serviço, o seu `*-infrastructure` (namespace e fila) e o seu `*-database-infrastructure` (banco). Ver [ADR 0021](adr/0021-infrastructure-per-service.md).

## A Saga

Aprovar um orçamento passa por três serviços, cada um com o seu banco, e não existe transação que cubra os três. O OS **orquestra**: envia um comando por vez, espera a resposta e decide o passo seguinte; se um passo falha, manda desfazer os anteriores. O estado fica na tabela `saga_instance` do OS e pode ser consultado em `GET /api/v1/service-orders/{id}/saga`. Decisão e alternativas no [ADR 0018](adr/0018-orchestrated-saga.md).

| Passo da saga | Quem executa | Resposta esperada |
|---|---|---|
| `GENERATE_BUDGET` | Billing | `BudgetGenerated` (total do orçamento) |
| `AWAIT_APPROVAL` | o cliente, pelo consultor ou pelo webhook | aprovação ou rejeição |
| `RESERVE_STOCK` | Inventory | `StockReserved` ou `StockReservationFailed` |
| `PROCESS_PAYMENT` | Billing | `PaymentApproved` ou `PaymentDeclined` |
| `ENQUEUE_EXECUTION` | Execution | `ExecutionEnqueued` ou `ExecutionEnqueueFailed` |
| `EXECUTING` | os mecânicos, no Execution | `ExecutionCompleted` |
| `CONSUME_STOCK` | Inventory | `StockConsumed` (passo pivô: nunca é desfeito) |

Estados da saga: `RUNNING`, `COMPLETED`, `REJECTED` (o cliente rejeitou o orçamento), `COMPENSATING` (um passo falhou e há desfazimentos pendentes) e `COMPENSATED`. A OS ganhou o status `CANCELLED` e continua em `WAITING_APPROVAL` enquanto a saga de aprovação roda.

### Caminho feliz

```mermaid
sequenceDiagram
    actor Con as Consultor
    actor Mec as Mecânico
    participant OS as OS Service
    participant BIL as Billing
    participant INV as Inventory
    participant EXE as Execution

    Mec->>OS: conclui o diagnóstico
    OS->>BIL: GenerateBudget (itens da OS)
    BIL-->>OS: BudgetGenerated (total)
    Note over OS: OS passa a WAITING_APPROVAL
    Con->>OS: aprova o orçamento (forma de pagamento)
    OS->>INV: ReserveStock
    INV-->>OS: StockReserved
    OS->>BIL: ProcessPayment
    BIL-->>OS: PaymentApproved
    OS->>EXE: EnqueueExecution (serviços da OS)
    EXE-->>OS: ExecutionEnqueued
    Note over OS: OS passa a IN_EXECUTION
    Mec->>EXE: inicia e conclui cada tarefa
    EXE-->>OS: ExecutionCompleted
    Note over OS: OS passa a FINISHED
    OS->>INV: ConsumeStock
    INV-->>OS: StockConsumed
    Note over OS: saga COMPLETED
```

### Compensação: pagamento recusado

O gateway de pagamento do Billing é um mock determinístico: recusa quando a forma de pagamento é `mock-decline` ou o valor passa de um limite configurável. A recusa é uma **resposta**, não um erro.

```mermaid
sequenceDiagram
    participant OS as OS Service
    participant BIL as Billing
    participant INV as Inventory

    OS->>INV: ReserveStock
    INV-->>OS: StockReserved
    OS->>BIL: ProcessPayment (mock-decline)
    BIL-->>OS: PaymentDeclined (motivo)
    Note over OS: OS passa a CANCELLED, saga COMPENSATING
    OS->>INV: ReleaseStock
    OS->>BIL: CancelBudget
    INV-->>OS: StockReleased
    BIL-->>OS: BudgetCancelled
    Note over OS: saga COMPENSATED
```

### Compensação: estoque insuficiente

Nada foi reservado, então só o orçamento precisa ser cancelado.

```mermaid
sequenceDiagram
    participant OS as OS Service
    participant BIL as Billing
    participant INV as Inventory

    OS->>INV: ReserveStock
    INV-->>OS: StockReservationFailed (estoque insuficiente)
    Note over OS: OS passa a CANCELLED, saga COMPENSATING
    OS->>BIL: CancelBudget
    BIL-->>OS: BudgetCancelled
    Note over OS: saga COMPENSATED
```

A terceira compensação (falha ao enfileirar a execução) manda `RefundPayment` ao Billing e `ReleaseStock` ao Inventory. O estorno do Billing já cancela o orçamento.

### Garantias de entrega

- **Idempotência em duas pontas:** o OS descarta mensagens já tratadas (`processed_message`) e respostas que não correspondem ao passo atual; cada serviço trata um comando repetido sem refazer o efeito (não cobra duas vezes, não tira estoque duas vezes) e responde com o mesmo `messageId`.
- **Falha vai para a DLQ:** uma mensagem que um serviço não consegue tratar volta à fila e, depois de 3 tentativas, vai para a DLQ da fila ([ADR 0020](adr/0020-sqs-sns-messaging.md)).
- **Sem outbox:** a resposta é publicada depois de gravar; uma falha no meio é refeita pela reentrega e absorvida pela idempotência.

## Diagramas de Sequência

### Autenticação via CPF

O token é emitido pelo OS e leva a claim `role`; os demais serviços validam a assinatura com o mesmo segredo e usam a role do próprio token, sem consultar banco ([ADR 0017](adr/0017-shared-jwt-secret-with-role-claim.md)).

```mermaid
sequenceDiagram
    actor F as Funcionário
    participant GW as API Gateway
    participant L as Lambda (auth-cpf)
    participant A as OS Service (EKS)
    participant DB as RDS MySQL

    F->>GW: POST /auth/token {cpf, password}
    GW->>L: invoke (AWS_PROXY)
    L->>L: valida checksum do CPF
    alt CPF mal formado
        L-->>GW: 400 Invalid CPF
        GW-->>F: 400 Invalid CPF
    else CPF com formato válido
        L->>A: POST /api/v1/auth/login {cpf, password}
        A->>DB: SELECT * FROM users WHERE cpf = ?
        DB-->>A: usuário (ou vazio)
        alt credenciais inválidas ou usuário não existe
            A-->>L: 401 Unauthorized
            L-->>GW: 401 Unauthorized
            GW-->>F: 401 Unauthorized
        else credenciais válidas
            A->>A: gera JWT com sub e role
            A-->>L: 200 {token}
            L-->>GW: 200 {token}
            GW-->>F: 200 {token}
        end
    end
    F->>GW: GET /api/v1/supplies<br/>Authorization: Bearer token
    GW->>GW: escolhe o serviço pelo prefixo (Inventory)
    GW->>Inventory: HTTP proxy
    Inventory->>Inventory: valida assinatura do JWT e a role
    Inventory-->>GW: 200
    GW-->>F: 200
```

### Abertura de Ordem de Serviço

```mermaid
sequenceDiagram
    actor C as Consultor
    participant GW as API Gateway
    participant A as OS Service (EKS)
    participant DB as RDS MySQL
    participant NR as New Relic

    C->>GW: POST /api/v1/service-orders {customerId, vehicleId}<br/>Authorization: Bearer token
    GW->>A: HTTP proxy (prefixo padrão)
    A->>A: valida JWT + role CONSULTANT/ADMIN
    A->>DB: valida que customer e vehicle existem
    DB-->>A: ok
    A->>DB: INSERT service_orders (status=RECEIVED, os_code=...)
    DB-->>A: ok
    A-->>NR: log estruturado (trace.id correlacionado)
    A-->>GW: 201 {osCode, status: RECEIVED}
    GW-->>C: 201
```

### Adição de uma peça à OS

É a única chamada síncrona entre serviços ([ADR 0019](adr/0019-supply-snapshot-over-rest.md)): o OS consulta o Inventory e guarda uma cópia do nome e do preço no item.

```mermaid
sequenceDiagram
    actor M as Mecânico
    participant OS as OS Service
    participant INV as Inventory
    participant DB as RDS MySQL

    M->>OS: POST /api/v1/service-orders/{id}/items {supplyId, quantity}
    OS->>INV: GET /api/v1/supplies/{supplyId} (token do mecânico)
    alt peça existe
        INV-->>OS: 200 {id, name, unitPrice}
        OS->>DB: grava o item com id, nome e preço da peça
        OS-->>M: 200 (OS com o item)
    else peça desconhecida
        INV-->>OS: 404
        OS-->>M: 404
    else Inventory indisponível ou recusa o token
        INV--xOS: erro ou timeout
        OS-->>M: 503 (nada é gravado)
    end
```

## Nível 1 — Contexto

Quem interage com o sistema e por quê:

```mermaid
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef external fill:#999999,stroke:#6b6b6b,color:#fff

    Cliente["Cliente<br/>[Pessoa]<br/>Consulta o status da sua OS<br/>via código público, sem login"]:::person
    Funcionario["Funcionário da Oficina<br/>[Pessoa]<br/>Consultor, Mecânico, Estoquista ou Admin<br/>autenticado via JWT"]:::person
    ExternoWebhook["Sistema Externo de Notificação<br/>[Sistema Externo]<br/>Confirma aprovação de orçamento e<br/>atualização de status por e-mail"]:::external

    Workshop["Workshop Management System<br/>[Sistema]<br/>Gerencia clientes, veículos, ordens de serviço,<br/>orçamento e pagamento, estoque de peças<br/>e a fila de execução de uma oficina mecânica"]:::system

    Cliente -- "Consulta status da OS<br/>GET /tracking/{osCode} — JSON/HTTPS" --> Workshop
    Funcionario -- "Login, cadastros, gestão de OS, estoque<br/>e execução — JSON/HTTPS + JWT" --> Workshop
    ExternoWebhook -- "POST /webhooks/estimate-approval<br/>POST /webhooks/email-status-update<br/>JSON/HTTPS + X-Webhook-Token" --> Workshop
```

## Nível 2 — Containers

```mermaid
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef store fill:#f4a6a6,stroke:#a85252,color:#000
    classDef msg fill:#b8e0b8,stroke:#4a8f4a,color:#000
    classDef external fill:#999999,stroke:#6b6b6b,color:#fff

    Funcionario["Funcionário da Oficina<br/>[Pessoa]"]:::person
    Cliente["Cliente<br/>[Pessoa]"]:::person
    ExternoWebhook["Sistema Externo de Notificação<br/>[Sistema Externo]"]:::external

    subgraph Workshop["Workshop Management System"]
        direction TB
        OS["OS Service<br/>[Spring Boot 3.2 / Java 17]<br/>OS, clientes, veículos, catálogo,<br/>autenticação; orquestra a saga"]:::container
        INV["Inventory Service<br/>[Spring Boot 3.2 / Java 17]<br/>peças, estoque e reservas"]:::container
        BIL["Billing Service<br/>[Spring Boot 3.2 / Java 17]<br/>orçamento e pagamento (mock)"]:::container
        EXE["Execution Service<br/>[Spring Boot 3.2 / Java 17]<br/>fila de execução, tarefas e tempos"]:::container
        DBOS[("MySQL 8<br/>[RDS]")]:::store
        DBINV[("PostgreSQL 16<br/>[RDS]")]:::store
        DBBIL[("PostgreSQL 16<br/>[RDS]")]:::store
        DBEXE[("DynamoDB")]:::store
        BUS["SQS + SNS<br/>comandos e respostas da saga"]:::msg
    end

    Cliente -- "GET /api/v1/tracking/{osCode}" --> OS
    Funcionario -- "REST + JWT Bearer<br/>pelo API Gateway" --> OS
    Funcionario -- "REST + JWT Bearer" --> INV
    Funcionario -- "REST + JWT Bearer" --> BIL
    Funcionario -- "REST + JWT Bearer" --> EXE
    ExternoWebhook -- "POST /api/v1/webhooks/*<br/>X-Webhook-Token" --> OS

    OS -- "JDBC" --> DBOS
    INV -- "JDBC" --> DBINV
    BIL -- "JDBC" --> DBBIL
    EXE -- "AWS SDK" --> DBEXE
    OS -- "REST: consulta de peça" --> INV
    OS <-- "comandos e respostas" --> BUS
    INV <-- "comandos e respostas" --> BUS
    BIL <-- "comandos e respostas" --> BUS
    EXE <-- "comandos e respostas" --> BUS
```

Cada serviço expõe o próprio Swagger UI e `/v3/api-docs` (rotas dentro do próprio container). Pelo API Gateway só o Swagger do OS aparece; o dos outros serviços fica no load balancer de cada um.

## Nível 3 — Componentes

Todos os serviços seguem a mesma estrutura em quatro camadas ([ADR 0001](adr/0001-clean-architecture.md)): `interfaces/rest` conhece só as interfaces de `application/port/in`, os casos de uso implementam essas interfaces e dependem de abstrações do domínio, e a `infrastructure` implementa essas abstrações apontando de volta para dentro. Um teste ArchUnit em cada serviço falha o build se uma camada violar essa regra.

### OS Service

```mermaid
flowchart TB
    classDef controller fill:#85bbf0,stroke:#5d82a8,color:#000
    classDef port fill:#c6e2ff,stroke:#5d82a8,color:#000
    classDef domain fill:#ffd580,stroke:#b38600,color:#000
    classDef infra fill:#b8e0b8,stroke:#4a8f4a,color:#000
    classDef db fill:#438dd5,stroke:#2e6295,color:#fff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff

    subgraph REST["interfaces/rest"]
        Controllers["Auth · Customer · Vehicle · ServiceCatalog<br/>ServiceOrder · Tracking · Webhook<br/>+ GlobalExceptionHandler"]:::controller
    end

    subgraph APP["application"]
        direction TB
        PortsIn["port/in<br/>um por operação de negócio"]:::port
        UseCases["usecase<br/>implementam os ports de entrada"]:::port
        Saga["saga<br/>SagaOrchestrator, comandos e eventos"]:::port
        PortsOut["port/out<br/>SagaCommandPublisher · UserAuthenticator<br/>WebhookTokenVerifier"]:::port
    end

    subgraph DOM["domain"]
        direction TB
        Model["model<br/>serviceorder · saga · customer · vehicle<br/>catalog · supply (cópia) · shared"]:::domain
        Repo["repository<br/>interfaces (inclui SagaInstance)"]:::domain
        Gateway["gateway<br/>SupplyCatalog"]:::domain
        DomSvc["service<br/>notificação do orçamento, métricas"]:::domain
    end

    subgraph INFRA["infrastructure"]
        direction TB
        Persist["persistence<br/>adapters JPA, entities, mappers"]:::infra
        Security["security<br/>filtro e emissão de JWT"]:::infra
        Inventory["inventory<br/>cliente REST do Inventory"]:::infra
        Messaging["messaging<br/>SqsSagaCommandPublisher<br/>SagaEventListener"]:::infra
        Obs["observability · notification"]:::infra
    end

    MySQL[("MySQL 8")]:::db
    Fila["SQS / SNS"]:::ext
    InvSvc["Inventory Service"]:::ext

    Controllers -- "chama" --> PortsIn
    PortsIn -. "implementado por" .-> UseCases
    UseCases -- "orquestra" --> Model
    UseCases -- "usa" --> Repo
    UseCases -- "usa" --> Gateway
    UseCases -- "dispara" --> Saga
    Saga -- "envia comandos por" --> PortsOut
    Repo -. "implementado por" .-> Persist
    Gateway -. "implementado por" .-> Inventory
    PortsOut -. "implementado por" .-> Messaging
    Persist -- "JDBC" --> MySQL
    Inventory -- "REST" --> InvSvc
    Messaging -- "SDK e listener" --> Fila
    Fila -- "respostas da saga" --> Messaging
    Messaging -- "handle(event)" --> Saga
    Security -- "intercepta requisições" --> Controllers
```

As setas tracejadas ("implementado por") são o ponto central da Clean Architecture: o domínio e a aplicação declaram o que precisam (`SupplyCatalog`, `SagaCommandPublisher`, os repositórios) e a infraestrutura implementa, de modo que trocar o SQS por outra fila, ou o REST do Inventory por outra forma de obter a peça, não toca no domínio.

### Inventory, Billing e Execution

| Serviço | Interfaces REST | Casos de uso e domínio | Infraestrutura |
|---|---|---|---|
| **Inventory** | `SupplyController`, `StockReservationController` (leitura) | CRUD de peças, ajuste de estoque e reserva (`ReserveStock`, `ReleaseStock`, `ConsumeStock`); domínio `Supply` e `StockReservation` | JPA/PostgreSQL com lock de linha, validação de JWT, listener de `inventory-commands`, publicador SNS |
| **Billing** | `BudgetController`, `PaymentController` (leitura) | `GenerateBudget`, `CancelBudget`, `ProcessPayment`, `RefundPayment`; domínio `Budget`, `Payment` e a port `PaymentGateway` | JPA/PostgreSQL, `MockPaymentGateway`, validação de JWT, listener de `billing-commands`, publicador SNS |
| **Execution** | `ExecutionController` (fila, iniciar e concluir tarefa, tempo médio) | `EnqueueExecution`, `StartTask`, `FinishTask`, `GetAverageExecutionTime`; domínio `ExecutionOrder` e `ExecutionTask` | adaptador DynamoDB com lock otimista, métrica `EXECUTION` no New Relic, validação de JWT, listener de `execution-commands`, publicador SNS |

Billing, Inventory e Execution só consultam por REST: orçamentos, pagamentos, reservas e a fila de execução são criados e alterados por comandos da saga, nunca por HTTP, para ninguém contornar o orquestrador.

## Decisões Técnicas

- **Quatro microsserviços, cada um com o seu banco** ([ADR 0015](adr/0015-split-into-four-services.md), [ADR 0016](adr/0016-database-per-service.md)): as fronteiras seguem quem é dono do dado e das regras. Clientes, veículos e catálogo ficam no OS por serem estáveis e lidos junto com a OS.
- **Saga orquestrada pelo OS** ([ADR 0018](adr/0018-orchestrated-saga.md)): substitui a transação única do monólito por passos com compensações, com um ponto de leitura do estado da saga.
- **SQS para comandos, SNS para respostas** ([ADR 0020](adr/0020-sqs-sns-messaging.md)): entrega "pelo menos uma vez" com idempotência nas duas pontas e DLQ por fila.
- **Uma única chamada síncrona entre serviços** ([ADR 0019](adr/0019-supply-snapshot-over-rest.md)): o OS consulta o Inventory ao adicionar uma peça e guarda uma cópia do nome e do preço.
- **JWT com segredo compartilhado e claim `role`** ([ADR 0017](adr/0017-shared-jwt-secret-with-role-claim.md)), stateless como no [ADR 0002](adr/0002-jwt-stateless-auth.md): cada serviço valida o token localmente e aplica as próprias regras de perfil, compatível com várias réplicas por trás do HPA ([DEPLOYMENT.md](DEPLOYMENT.md)).
- **Clean Architecture em cada serviço, verificada por ArchUnit** ([ADR 0001](adr/0001-clean-architecture.md)): `Repository`, não `Gateway`, para os acessos a dados, por consistência com a terminologia de Spring Data/DDD. O domínio é POJO puro, testável sem subir o Spring.
- **Duplicação consciente em vez de biblioteca compartilhada** ([ADR 0012](adr/0012-duplicated-cpf-validator.md)): cada serviço tem o seu `Money`, o seu filtro de JWT e o seu envelope de mensagem, para os repositórios continuarem independentes.
- **Infraestrutura como código por serviço** ([ADR 0021](adr/0021-infrastructure-per-service.md)): descoberta do cluster por nome, ResourceQuota por namespace, `apply` manual e ordem de subida e descida documentada.
- **Consistência eventual assumida**: no monólito, aprovar o orçamento baixava o estoque na mesma transação; agora a OS só vira `IN_EXECUTION` depois que estoque, pagamento e fila confirmam, e o progresso fica visível em `GET /service-orders/{id}/saga`.
