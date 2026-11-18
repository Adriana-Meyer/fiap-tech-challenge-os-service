FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system spring && adduser --system spring --ingroup spring

# New Relic Java agent — attached via -javaagent below. Configured
# entirely through env vars (NEW_RELIC_LICENSE_KEY, NEW_RELIC_APP_NAME,
# etc. — see k8s Secret/ConfigMap), no newrelic.yml committed to the repo.
ARG NEWRELIC_AGENT_VERSION=8.15.0
ADD https://repo1.maven.org/maven2/com/newrelic/agent/java/newrelic-agent/${NEWRELIC_AGENT_VERSION}/newrelic-agent-${NEWRELIC_AGENT_VERSION}.jar /app/newrelic.jar

COPY --from=build /app/target/management-1.0.0-SNAPSHOT.jar app.jar
RUN chown spring:spring app.jar newrelic.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/newrelic.jar", "-jar", "app.jar"]
