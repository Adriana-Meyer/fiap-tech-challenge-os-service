# RFC 0003: Estratégia de autenticação

**Status**: Aceito
**Data**: 2026-09-13

## Resumo

A Fase 3 exige proteger as rotas sensíveis com autenticação via CPF, através de uma Function Serverless atrás de um API Gateway, que deve validar o CPF, consultar existência/status do usuário, e devolver um JWT.

## Contexto

A App já tem autenticação JWT stateless via Spring Security (`JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`), com RBAC por role (`ADMIN`, `CONSULTANT`, `MECHANIC`, `STOCKIST`) testado desde a Fase 1. Foram avalidadas duas formas de a Lambda participar: (a) como **Lambda Authorizer** dentro do API Gateway, validando o token a cada requisição; ou (b) a **Lambda consultar a própria App**, que continua fazendo a validação como já faz hoje.

## Opções consideradas

### Opção A — Lambda Authorizer

A Lambda validaria o JWT (assinatura, expiração, claims) a cada requisição ao API Gateway, antes de encaminhar para a App.

- Prós: centraliza a decisão de autorização na borda.
- Contras: duplica a lógica de validação de JWT (hoje só na App), exige compartilhar o segredo de assinatura do JWT com a Lambda, e a App precisaria confiar cegamente em requisições vindas do Gateway (sem revalidar) ou continuar validando de qualquer forma — tornando o Authorizer redundante na prática.

### Opção B — Lambda consulta a própria App (escolhida)

A Lambda participa só da **emissão** do token: valida o formato/checksum do CPF (rejeição rápida de entradas malformadas, sem round-trip de banco) e delega a autenticação de verdade para o endpoint de login já existente na App (`POST /api/v1/auth/login`, adaptado para `{cpf, senha}` — [ADR 0006](../adr/0006-cpf-replaces-email-login.md)). Todas as demais rotas passam por HTTP proxy direto da App, que continua validando o JWT e o RBAC exatamente como hoje (`JwtAuthenticationFilter`, `SecurityConfig`).

## Justificativa

- **Zero duplicação de lógica de segurança**: a App continua sendo a única fonte de verdade de autenticação e autorização — o segredo de assinatura do JWT nunca precisa ser compartilhado com a Lambda.
- **Menor superfície de mudança**: `JwtAuthenticationFilter`/`SecurityConfig` não mudam nada — toda regra de RBAC por rota continua válida sem revisão.
- **Risco reduzido**: um bug na Lambda (runtime diferente, sem os mesmos testes da App) não pode, na pior hipótese, emitir ou aceitar um token inválido — ela nunca gera nem valida tokens, só repassa.

## Consequências

- A Lambda é uma dependência de disponibilidade adicional só para a emissão de novos tokens — se a Lambda cair, tokens já emitidos continuam válidos (JWT stateless), só não é possível fazer *login* novo via o caminho oficial do API Gateway.
- O NLB da App (os-service) fica, por [ADR 0009](../adr/0009-nlb-over-alb.md), tecnicamente alcançável direto (contornando API Gateway/Lambda) — aceito como trade-off documentado; a App continua validando CPF em formato básico (`@Pattern`) independentemente da origem da requisição.
