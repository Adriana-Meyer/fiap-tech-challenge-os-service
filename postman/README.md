# Coleção do Postman

Fluxo completo do sistema pelos quatro serviços (OS, Billing, Inventory e Execution), com testes automáticos em cada passo. Serve para explorar a API e para demonstrar a saga, inclusive as compensações.

| Arquivo | Conteúdo |
|---|---|
| `workshop-saga.postman_collection.json` | a coleção |
| `local.postman_environment.json` | ambiente local: os serviços do [`e2e/`](../e2e/README.md) nas portas 8080 a 8083 |
| `aws.postman_environment.json` | ambiente da AWS: tudo pelo API Gateway (preencha `gatewayUrl`) |

## O que a coleção faz

| Pasta | O que percorre |
|---|---|
| **0. Autenticação** | login do administrador, do consultor e do mecânico (usuários do seed); guarda os tokens |
| **1. Fluxo feliz** | abre a OS, diagnóstico, orçamento calculado pelo Billing, aprovação, estoque reservado, pagamento aprovado, execução enfileirada, mecânico conclui a tarefa, OS `FINISHED`, saga `COMPLETED` e entrega |
| **2. Pagamento recusado** | aprova com a forma de pagamento `mock-decline`: o estoque volta, a reserva é liberada, o pagamento fica `DECLINED` e o orçamento `CANCELLED` |
| **3. Estoque insuficiente** | pede 11 discos de freio para um estoque de 10: nenhuma reserva, nenhuma cobrança, orçamento `CANCELLED` |
| **Consultas úteis** | saga e OS atuais, peças, acompanhamento público e saúde |

A saga é assíncrona, então os passos "Aguardar…" repetem a consulta, uma por segundo (até 60 vezes), até a OS ou a saga chegar ao estado esperado.

## Como usar

1. Importe os três arquivos no Postman (*Import*) e escolha o ambiente **Workshop: local (e2e)** ou **Workshop: AWS (API Gateway)**.
2. No ambiente local, suba os serviços: `docker compose -f e2e/docker-compose.yml up -d --build`.
3. Rode a pasta **0. Autenticação** e depois uma das pastas **1** a **3** no *Collection Runner*. Cada pasta é independente e abre uma OS nova.

No Postman normal, sem o Runner, o "Aguardar…" não se repete sozinho: reenvie a requisição até ela passar.

### Pela linha de comando (Newman)

```bash
npx newman run postman/workshop-saga.postman_collection.json \
  -e postman/local.postman_environment.json
```

Para rodar só um cenário, use `--folder "1. Fluxo feliz: orçamento aprovado e pago leva a OS até a entrega"` (rode antes `--folder "0. Autenticação (rode primeiro)"` na mesma execução, repetindo a opção).

## Ambiente da AWS

Em `aws.postman_environment.json`, troque `gatewayUrl` pela URL do API Gateway (saída do `terraform apply` do repositório do gateway). O login vai por `POST /auth/token` (a Lambda valida o CPF e chama o OS) e as demais rotas seguem para o serviço dono de cada prefixo.

## Observações

- A coleção usa os dados do seed: o serviço "Troca de óleo", o "Filtro de óleo" (estoque 40) e o "Disco de freio" (estoque 10). Se o estoque de discos de freio já tiver sido alterado, o cenário 3 pode não recusar a reserva.
- O cliente (CPF `52998224725`) e o veículo (placa `ABC1D23`) são criados na primeira execução e reaproveitados nas seguintes.
- Os tokens e os ids ficam em variáveis da coleção; não há credenciais reais no arquivo (as senhas são as do seed, as mesmas do [README](../README.md#usuários-padrão-seed-v3--v4)).
