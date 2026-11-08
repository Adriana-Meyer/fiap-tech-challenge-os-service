# ADR 0011: Lambda de autenticação em Java, não Node.js

**Status**: Aceito
**Data**: 2026-09-13

## Contexto

AWS Lambda suporta múltiplas runtimes (Node.js, Python, Java, Go, .NET, Ruby, entre outras) — não há restrição técnica que force uma linguagem específica. A função em questão ([fiap-tech-challenge-API-gateway-function-serverless](https://github.com/Adriana-Meyer/fiap-tech-challenge-API-gateway-function-serverless)) é deliberadamente fina: valida o checksum de um CPF e faz uma chamada HTTP para o endpoint de login da App.

## Decisão

Implementar a Lambda em Java 17 + Maven, e não em Node.js (a opção inicialmente cogitada por ter cold start mais rápido para uma função tão simples).

Motivo: manter uma única linguagem de aplicação em todo o projeto (a App já é Java/Spring Boot) e aproveitar o projeto como oportunidade de aprofundamento na linguagem Java.

Mitigações para o trade-off de cold start (Java tipicamente 1-2s vs. ~100-300ms do Node, nesta função sem framework):
- Sem Spring nem qualquer framework de aplicação — só `aws-lambda-java-core`/`events` + Jackson, evitando o custo de inicialização de um contexto de aplicação.
- `java.net.http.HttpClient` (nativo do JDK) para a chamada HTTP, sem dependência de cliente HTTP externa.
- `memory_size = 512` (Lambda aloca CPU proporcional à memória, acelerando o cold start).

## Consequências

- Empacotamento via `maven-shade-plugin` (uber-jar) é necessário — mais um passo de build (`mvn package`) antes do `terraform apply`/`validate`, ausente numa Lambda Node.js baseada em `archive_file` puro.
- Cold start de ~1-2s na primeira chamada após período ocioso — irrelevante para o volume de uso de um projeto acadêmico.
- `CpfValidator` da Lambda duplica o algoritmo já existente na App (`domain/model/shared/CpfValidator.java`) — ver [ADR 0012](0012-duplicated-cpf-validator.md).
