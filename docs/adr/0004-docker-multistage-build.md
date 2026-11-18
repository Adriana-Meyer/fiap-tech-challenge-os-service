# ADR 0004: Docker multi-stage build com usuário non-root

**Status**: Aceito
**Data**: 2026-07-12

## Contexto

A App precisa rodar como contêiner para ser deployável em Kubernetes. Um `Dockerfile` ingênuo (uma única stage com JDK completo + Maven + código-fonte na imagem final) gera imagens desnecessariamente grandes (todo o toolchain de build embutido) e frequentemente roda o processo como root dentro do contêiner — dois problemas de tamanho de imagem e de segurança.

## Decisão

`Dockerfile` multi-stage:
- **Stage `build`**: `maven:3.9-eclipse-temurin-17`, resolve dependências e empacota o jar (`mvn clean package -DskipTests`) — testes já rodam antes, no pipeline de CI, não durante o build da imagem.
- **Stage final**: `eclipse-temurin:17-jre` (só JRE, sem JDK/Maven), copia apenas o jar já compilado, cria e usa um usuário `spring` não-privilegiado (`addgroup`/`adduser --system`) para rodar o processo.

Depois, esse mesmo Dockerfile passou a também baixar e anexar o agente Java da New Relic (`-javaagent`, [ADR 0013](0013-newrelic-custom-events-for-status-metric.md)) na stage final.

**Atualização (2026-11-15):** as duas imagens passaram para o Java 21 (`maven:3.9-eclipse-temurin-21` e `eclipse-temurin:21-jre`), conforme o [ADR 0022](0022-java-21.md). O desenho multi-stage e o usuário não-root não mudaram.

## Consequências

- Imagem final não carrega Maven, código-fonte nem dependências de build — só o runtime necessário, reduzindo tamanho e superfície de ataque.
- Processo roda como usuário não-root dentro do contêiner — mitiga o impacto de uma eventual vulnerabilidade de escape de contêiner.
- Todo `docker build` reexecuta `mvn clean package` (sem cache de camada entre builds distintos do código-fonte) — aceito em troca de reprodutibilidade; o cache de dependências (`dependency:go-offline`) já é isolado numa camada própria, então só re-resolve dependências quando o `pom.xml` muda.
