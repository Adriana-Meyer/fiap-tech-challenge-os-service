# ADR 0019: Peça na OS como cópia, obtida por REST síncrono do Inventory

**Status**: Aceito
**Data**: 2026-11-08

## Contexto

O item de uma OS apontava para a peça (`Supply`) por chave estrangeira, no mesmo banco. Com o estoque no Inventory ([ADR 0015](0015-split-into-four-services.md)) a OS não tem mais a tabela de peças, e o preço e o nome da peça na OS não devem mudar se o cadastro do estoque mudar depois.

## Decisão

Ao adicionar uma peça a uma OS, o OS **consulta o Inventory por REST** (`GET /api/v1/supplies/{id}`) e guarda no item uma **cópia**: o id, o nome e o preço (o preço do item já era armazenado).

- A chamada leva o **token do usuário** que está adicionando o item: o Inventory vê quem pediu e aplica as próprias regras de perfil, e não há conta de serviço para administrar.
- Timeouts curtos (2 s para conectar, 3 s para ler): quem está adicionando o item espera pela resposta.
- Peça inexistente no Inventory vira **404**; Inventory fora do ar, ou recusando o token, vira **503** com mensagem clara, e nada é gravado.
- O `supply_id` do item **não é chave estrangeira** (é só o id guardado), e a migration copia o nome da peça para o item antes de remover a tabela de peças do OS.

É a **única chamada síncrona entre serviços**: o resto da comunicação é assíncrono e feito pela saga ([ADR 0020](0020-sqs-sns-messaging.md)).

## Por que REST e não uma mensagem

O usuário precisa de uma resposta imediata (a peça existe? qual o preço?) para montar a OS, e a resposta vira parte do dado da OS naquele instante. Um fluxo assíncrono exigiria um estado intermediário "item aguardando confirmação" que não traz benefício.

## Consequências

- **O preço fica fixado na inclusão**: um reajuste posterior no Inventory não altera OS já montadas, o que é o comportamento esperado de um orçamento.
- **Acoplamento temporal nessa operação**: sem o Inventory não se adiciona peça a uma OS (serviços do catálogo continuam funcionando).
- A chamada acontece dentro da transação do OS, então segura uma conexão do banco por até alguns segundos no pior caso.
- Em reservas de estoque a OS não repassa preços: envia só o id e a quantidade.
