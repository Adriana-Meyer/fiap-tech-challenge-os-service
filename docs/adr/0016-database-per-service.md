# ADR 0016: Um banco por serviço, com o motor adequado a cada um

**Status**: Aceito
**Data**: 2026-11-07

## Contexto

Com a divisão em serviços ([ADR 0015](0015-split-into-four-services.md)), compartilhar um banco entre eles recriaria o acoplamento que a separação quis desfazer: um serviço poderia ler ou alterar a tabela do outro, e qualquer mudança de esquema exigiria coordenar vários deploys.

## Decisão

Cada serviço tem o seu banco, e **nenhum serviço tem credencial nem rota de rede até o banco de outro**. O que um serviço precisa saber do dado de outro chega por mensagem (a saga) ou por uma cópia guardada no momento certo (o [ADR 0019](0019-supply-snapshot-over-rest.md)).

| Serviço | Banco | Por quê |
|---|---|---|
| OS | MySQL 8 (RDS) | Mantido da Fase 3: dados fortemente relacionais (cliente → veículo → OS → itens) e migrations Flyway já existentes. |
| Billing | PostgreSQL 16 (RDS) | Orçamento e pagamento são relacionais e transacionais; uma restrição `UNIQUE` por OS garante um orçamento e um pagamento por ordem. |
| Inventory | PostgreSQL 16 (RDS) | A reserva de estoque exige transação ("tudo ou nada") e lock de linha para não vender o mesmo item duas vezes. |
| Execution | DynamoDB | O acesso é sempre por chave (a OS) e quase sempre à ordem inteira: um item por OS, com as tarefas aninhadas, e um atributo de versão como lock otimista entre mecânicos. Sob demanda, sem capacidade para dimensionar e sem custo parado. |

Os quatro motores aparecem nos repositórios de banco: três `*-database-infrastructure` novos (Billing, Inventory e Execution) e o já existente do OS.

## Consequências

- **Sem junções entre serviços**: relatórios que cruzariam dados de dois serviços precisam de uma chamada ou de uma cópia. Por isso a análise de tempo de execução mora no Execution.
- **Três instâncias RDS ligadas ao mesmo tempo** (OS, Billing e Inventory) custam mais no Learner Lab que uma só. Ficam em single-AZ, `db.t3.micro` ([ADR 0008](0008-rds-single-az.md)), e a recomendação é `destroy` entre as sessões.
- **Entre bancos não há transação**: a consistência entre eles é responsabilidade da saga e da idempotência dos handlers.
- As credenciais de cada RDS chegam ao repositório do serviço como secrets, enviados pelo `terraform-apply.yml` do repositório de banco, sem aparecer em log. O DynamoDB não tem senha: os pods o acessam pelo role IAM do nó.
