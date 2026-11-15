package com.fiap.workshop.management.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.workshop.management.integration.BaseIntegrationTest;
import com.fiap.workshop.management.integration.SagaTestConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;

/**
 * The environment of the scenarios: the OS service with the real MySQL (from
 * {@link BaseIntegrationTest}) and the real queues and topic on LocalStack,
 * created as the infrastructure repositories will create them. The other three
 * services are {@link SimulatedServices}, listening on their command queues.
 */
@CucumberContextConfiguration
@SpringBootTest
@Import({SagaTestConfig.class, BddSpringConfiguration.Participants.class})
@TestPropertySource(properties = "test.recording-publisher=false")
public class BddSpringConfiguration extends BaseIntegrationTest {

    @SuppressWarnings("resource")
    static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
                    .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS);

    private static SqsClient sqs;
    private static SnsClient sns;
    private static String topicArn;
    private static String billingUrl;
    private static String inventoryUrl;
    private static String executionUrl;

    static {
        localstack.start();
        createTopology();
    }

    private static void createTopology() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        Region region = Region.of(localstack.getRegion());
        sqs = SqsClient.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials).region(region).build();
        sns = SnsClient.builder().endpointOverride(localstack.getEndpoint())
                .credentialsProvider(credentials).region(region).build();

        billingUrl = sqs.createQueue(r -> r.queueName("billing-commands")).queueUrl();
        inventoryUrl = sqs.createQueue(r -> r.queueName("inventory-commands")).queueUrl();
        executionUrl = sqs.createQueue(r -> r.queueName("execution-commands")).queueUrl();

        String deadLettersArn = arnOf(sqs.createQueue(r -> r.queueName("os-saga-events-dlq")).queueUrl());
        String eventsUrl = sqs.createQueue(r -> r.queueName("os-saga-events").attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "5",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"3\"}".formatted(deadLettersArn)))).queueUrl();

        topicArn = sns.createTopic(r -> r.name("saga-events")).topicArn();
        sns.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(arnOf(eventsUrl))
                .attributes(Map.of("RawMessageDelivery", "true")));
    }

    private static String arnOf(String queueUrl) {
        return sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.messaging.enabled", () -> "true");
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localstack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    }

    @TestConfiguration
    static class Participants {

        @Bean(destroyMethod = "close")
        SimulatedServices simulatedServices(ObjectMapper objectMapper) {
            return new SimulatedServices(sqs, sns, topicArn, objectMapper, billingUrl, inventoryUrl, executionUrl);
        }
    }
}
