# ADR 0002: JWT stateless para autenticação

**Status**: Aceito
**Data**: 2026-05-02

## Contexto

A API precisa autenticar funcionários (Consultor, Mecânico, Estoquista, Admin) e autorizar operações por papel (RBAC). O sistema roda atrás de um HPA (ver [ADR 0003](0003-kubernetes-hpa.md)), potencialmente com múltiplas réplicas simultâneas.

## Decisão

Autenticação via **JWT stateless**: `POST /api/v1/auth/login` retorna um token assinado (`JwtService`, JJWT), enviado pelo cliente em `Authorization: Bearer <token>` nas requisições seguintes. Nenhuma sessão é mantida no servidor — `JwtAuthenticationFilter` valida o token e popula o contexto de segurança do Spring a cada requisição, sem consultar nenhum estado compartilhado.

## Consequências

- Compatível com múltiplas réplicas por trás do HPA sem necessidade de sticky sessions nem de um store de sessão compartilhado (ex.: Redis) — qualquer réplica valida qualquer token, já que a validação depende só da assinatura, não de estado.
- O segredo de assinatura (`JWT_SECRET`) precisa ser idêntico em todas as réplicas — hoje via variável de ambiente/Secret do Kubernetes.
- Revogar um token antes da expiração natural não é possível sem introduzir uma blocklist com estado — não implementado; os tokens expiram em 24h (`app.jwt.expiration-ms`).
- Depois, o identificador do "subject" do token passou de e-mail para CPF ([ADR 0006](0006-cpf-replaces-email-login.md)) — `JwtService` não precisou mudar, por ser agnóstico ao formato do identificador.
