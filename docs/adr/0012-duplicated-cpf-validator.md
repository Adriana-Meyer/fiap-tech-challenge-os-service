# ADR 0012: Validação de CPF duplicada entre a App e a Lambda, não compartilhada via lib

**Status**: Aceito
**Data**: 2026-09-13

## Contexto

O algoritmo de checksum de CPF (dígitos verificadores) existe em dois lugares: `domain/model/shared/CpfValidator.java` (os-service, usado pelo `Document` do Customer) e `lambda/auth/.../CpfValidator.java` ([fiap-tech-challenge-API-gateway-function-serverless](https://github.com/Adriana-Meyer/fiap-tech-challenge-API-gateway-function-serverless), usado na validação de CPF antes de delegar o login para a App). São ~20 linhas idênticas.

## Decisão

Manter a duplicação, em vez de extrair uma biblioteca compartilhada (ex.: publicada no GitHub Packages) ou usar um git submodule entre os repositórios.

Motivo: a Fase 3 exige 4 repositórios genuinamente independentes, cada um com seu próprio CI/CD, sem exigência de estarem interligados. Uma lib compartilhada acoplaria o build da Lambda à publicação prévia de um artefato pela App — o oposto do objetivo de independência entre repositórios. O algoritmo em si é um padrão fixo do governo brasileiro (não muda), o que reduz bastante o risco prático de as duas cópias saírem de sincronia.

## Consequências

- Qualquer mudança futura no algoritmo (improvável, dado que é um padrão externo fixo) precisaria ser replicada manualmente nos dois repositórios — mitigado por um comentário em cada arquivo referenciando o outro.
- Nenhuma dependência de build entre o [fiap-tech-challenge-API-gateway-function-serverless](https://github.com/Adriana-Meyer/fiap-tech-challenge-API-gateway-function-serverless) e o os-service.
