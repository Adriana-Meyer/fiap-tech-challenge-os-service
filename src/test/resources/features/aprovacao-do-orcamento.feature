# language: pt
Funcionalidade: Aprovação do orçamento como saga orquestrada
  Aprovar um orçamento envolve o Inventory, o Billing e o Execution, cada um com o seu banco.
  O OS orquestra os passos por SQS e SNS e, se um deles falha, manda desfazer os anteriores.
  Aqui o OS roda de verdade, com MySQL e LocalStack; os outros três serviços são simulados.

  Contexto:
    Dado um serviço "Troca de óleo" de R$ 150,00 no catálogo
    E uma peça "Filtro de óleo" de R$ 25,00 com 10 unidades no estoque do Inventory
    E uma OS em diagnóstico com 1 "Troca de óleo" e 2 "Filtro de óleo"

  Cenário: Orçamento aprovado e pago leva a OS até o fim
    Quando o mecânico conclui o diagnóstico
    Então o Billing calcula um orçamento de R$ 200,00 e a OS aguarda a aprovação
    Quando o consultor aprova o orçamento pagando com "cartão"
    Então a OS fica em execução
    E o estoque do Inventory fica com 8 unidades de "Filtro de óleo"
    E o Billing registra o pagamento como aprovado
    Quando os mecânicos concluem a execução
    Então a OS fica finalizada
    E a saga fica concluída
    E a reserva de estoque da OS fica consumida

  Cenário: Pagamento recusado devolve o estoque e cancela o orçamento
    Dado que o orçamento foi calculado e a OS aguarda a aprovação
    Quando o consultor aprova o orçamento pagando com "mock-decline"
    Então a OS fica cancelada
    E a saga fica compensada, com o motivo "mock-decline"
    E o estoque do Inventory volta a 10 unidades de "Filtro de óleo"
    E a reserva de estoque da OS fica liberada
    E o Billing registra o pagamento como recusado
    E o Billing registra o orçamento como cancelado

  Cenário: Estoque insuficiente cancela o orçamento sem cobrar nada
    Dado que o estoque do Inventory tem apenas 1 unidade de "Filtro de óleo"
    E que o orçamento foi calculado e a OS aguarda a aprovação
    Quando o consultor aprova o orçamento pagando com "cartão"
    Então a OS fica cancelada
    E a saga fica compensada, com o motivo "Insufficient stock"
    E o estoque do Inventory continua com 1 unidade de "Filtro de óleo"
    E o Billing nunca recebeu um pedido de pagamento
    E o Billing registra o orçamento como cancelado

  Cenário: Falha ao enfileirar a execução estorna o pagamento e devolve o estoque
    Dado que o Execution não consegue enfileirar a OS
    E que o orçamento foi calculado e a OS aguarda a aprovação
    Quando o consultor aprova o orçamento pagando com "cartão"
    Então a OS fica cancelada
    E a saga fica compensada, com o motivo "Queue unavailable"
    E o Billing registra o pagamento como estornado
    E o Billing registra o orçamento como cancelado
    E o estoque do Inventory volta a 10 unidades de "Filtro de óleo"
