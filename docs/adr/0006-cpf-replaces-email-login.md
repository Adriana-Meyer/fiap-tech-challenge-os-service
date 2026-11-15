# ADR 0006: CPF substitui e-mail como identificador de login

**Status**: Aceito
**Data**: 2026-09-12

## Contexto

O projeto exige proteger as rotas que já exigem token com autenticação via CPF, através de uma Function Serverless. No domínio atual, porém, só o funcionário (tabela `users`) faz login (e-mail + senha) — o CPF só existe como documento do Customer, sem relação com autenticação. Não há uma noção de "cliente autenticado" no sistema hoje; o cliente só consulta o status da sua OS publicamente, sem login.

## Decisão

O CPF substitui o e-mail como identificador de login dos funcionários (`users.email` → `users.cpf`, migration V4). A senha continua obrigatória — o login passa a ser `{cpf, password}` em vez de `{email, password}`.

Alternativas consideradas:
- **CPF sozinho, sem senha**: rejeitada por reduzir a segurança de rotas que hoje protegem operações sensíveis (estoque, preços, ordens de serviço) — CPF não é secreto.
- **CPF como autenticação de cliente, separada do login de funcionário**: rejeitada porque o domínio atual não tem nenhuma rota protegida voltada a cliente (só tracking público) — criar esse fluxo do zero seria escopo novo, não a evolução pedida.

## Consequências

- `domain/model/shared/CpfValidator` (extraído de `Document`) valida o checksum do CPF, reaproveitado tanto pelo cadastro de clientes quanto potencialmente pelo login.
- `AuthRequest`, `UserJpaEntity`, `UserJpaRepository`, `UserDetailsServiceImpl`, `AuthController` passam a operar sobre `cpf` em vez de `email`. `JwtService` não muda — já era agnóstico ao formato do "username".
- O termo "cliente" no requisito de autenticação é interpretado de forma generalizada como "quem autentica", não literalmente a entidade `Customer` — decisão registrada aqui para deixar essa interpretação explícita.
- Ver também [RFC 0003 — Estratégia de autenticação](../rfc/0003-authentication-strategy.md).
