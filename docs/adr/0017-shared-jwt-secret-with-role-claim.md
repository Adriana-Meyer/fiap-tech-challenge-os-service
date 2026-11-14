# ADR 0017: JWT com segredo compartilhado e claim `role`

**Status**: Aceito
**Data**: 2026-11-07

## Contexto

O [ADR 0002](0002-jwt-stateless-auth.md) adotou JWT stateless. Na Fase 3 o token carregava só o `sub` (o CPF), e a role era relida do banco (`users`) a cada requisição. Com a divisão em serviços ([ADR 0015](0015-split-into-four-services.md)), só o OS tem a tabela de usuários; Inventory, Billing e Execution precisam autorizar por perfil (`ADMIN`, `CONSULTANT`, `MECHANIC`, `STOCKIST`) sem ter esse banco.

## Decisão

- O **OS continua sendo o único emissor**. O token passa a trazer a claim `role`.
- Os outros três serviços **só validam**: verificam a assinatura HMAC (HS256) com o mesmo `JWT_SECRET` e usam o `sub` e a `role` do próprio token, sem consultar banco nenhum. Um token sem `role` é tratado como não autenticado (401).
- Cada serviço aplica as suas próprias regras de perfil nas suas rotas. O API Gateway só roteia ([ADR 0021](0021-infrastructure-per-service.md)); não valida token.
- O `sub` do token é usado pelo Execution como o mecânico que assumiu a tarefa.

## Alternativas

- **RS256 (chave assimétrica):** só o OS teria a chave privada e os demais, a pública. É o desenho mais seguro para produção e **foi descartado só pelo prazo e pela complexidade de distribuir chaves no Lab**. É a evolução recomendada.
- **Introspecção** (cada serviço pergunta ao OS se o token vale): acoplaria o funcionamento de todos os serviços à disponibilidade do OS a cada requisição.
- **Lambda Authorizer no gateway:** duplicaria a lógica de perfis fora dos serviços, contra o raciocínio do [ADR 0011](0011-java-lambda.md).

## Consequências

- **Quem tem o segredo pode forjar um token.** O `JWT_SECRET` é o mesmo nos quatro serviços e guardado como secret do GitHub e do Kubernetes; trocá-lo exige redeploy dos quatro.
- **A role vale até o token expirar** (24 h): se o perfil de um usuário mudar no OS, os outros serviços só percebem no próximo token. Aceito para o escopo acadêmico.
- Nenhum serviço novo precisa de tabela de usuários, e a validação é local e rápida.
