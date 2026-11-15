# ADR 0001: Clean Architecture com inversão de dependência

**Status**: Aceito
**Data**: 2026-07-12 (decisão original do início do projeto, refinada num refactor dedicado)

## Contexto

O projeto começou como uma API de gestão de oficina mecânica (clientes, veículos, catálogo de serviços, peças, ordens de serviço) com um domínio de negócio real (cálculo de orçamento, controle de estoque, ciclo de vida de OS). Sem uma separação clara de camadas, regras de negócio tendem a se espalhar por controllers e repositórios, ficando difíceis de testar isoladamente e acopladas ao framework (Spring).

## Decisão

Adotar Clean Architecture em quatro camadas, com inversão de dependência:

- `domain` — modelo de negócio (`model`), abstrações de persistência (`repository`) e de serviços externos (`service`), exceções de domínio. POJO puro, **sem nenhuma dependência de Spring ou de qualquer framework** — testável sem subir contexto algum.
- `application` — casos de uso (`usecase`, um por operação de negócio) que implementam interfaces de entrada (`port/in`) e orquestram o domínio; DTOs (`dto`) de entrada/saída.
- `infrastructure` — adapters que implementam as abstrações do domínio: persistência JPA (`persistence`), segurança (`security`), notificações (`notification`), observabilidade (`observability`), configuração (`config`).
- `interfaces/rest` — controllers REST, que conhecem só as interfaces de `application/port/in`.

As setas de dependência sempre apontam para dentro: `infrastructure` depende de `domain`, nunca o contrário — as interfaces de repositório/serviço ficam em `domain`, e são implementadas por classes em `infrastructure` (inversão de dependência).

## Consequências

- O domínio (`domain/model`) é testável com JUnit puro, sem Spring Boot Test, sem banco — testes rápidos e isolados (ver `domain/model/serviceorder/ServiceOrderTest`, `domain/model/customer/DocumentTest`, etc.).
- Toda nova integração externa (ex.: New Relic — [ADR 0013](0013-newrelic-custom-events-for-status-metric.md)) segue o mesmo padrão porta/adapter: interface em `domain/service`, implementação em `infrastructure`, nunca o inverso.
- Curva de entrada maior para quem não conhece o padrão: uma operação de negócio simples toca em vários arquivos (port, use case, DTOs, controller) em vez de um único controller "inflado".
- Trade-off aceito deliberadamente em favor de manutenibilidade e testabilidade a longo prazo, mesmo custando mais boilerplate por operação.
