package com.fiap.workshop.management.integration;

import com.fiap.workshop.management.application.port.out.SagaCommandPublisher;
import com.fiap.workshop.management.application.saga.command.SagaCommand;
import com.fiap.workshop.management.domain.gateway.SupplyCatalog;
import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for everything outside the OS service: the other services (a
 * publisher that records the commands the saga sends, so a test can answer
 * them) and Inventory's REST API (a catalog the test fills).
 */
@TestConfiguration
public class SagaTestConfig {

    public static class RecordingPublisher implements SagaCommandPublisher {

        private final List<SagaCommand> commands = new CopyOnWriteArrayList<>();

        @Override
        public void publish(SagaCommand command) {
            commands.add(command);
        }

        public void reset() {
            commands.clear();
        }

        public List<SagaCommand> commands() {
            return List.copyOf(commands);
        }

        /** The most recent command of this type. */
        public <T extends SagaCommand> T last(Class<T> type) {
            return commands.stream().filter(type::isInstance).map(type::cast).reduce((a, b) -> b)
                    .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " was sent; sent: " + commands));
        }
    }

    public static class FakeSupplyCatalog implements SupplyCatalog {

        private final Map<UUID, SupplySnapshot> supplies = new ConcurrentHashMap<>();

        public void register(SupplySnapshot supply) {
            supplies.put(supply.id(), supply);
        }

        @Override
        public Optional<SupplySnapshot> findById(UUID supplyId) {
            return Optional.ofNullable(supplies.get(supplyId));
        }
    }

    /** Off in the tests that run the real queues ({@code test.recording-publisher=false}). */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.recording-publisher", havingValue = "true", matchIfMissing = true)
    public RecordingPublisher recordingPublisher() {
        return new RecordingPublisher();
    }

    @Bean
    @Primary
    public FakeSupplyCatalog fakeSupplyCatalog() {
        return new FakeSupplyCatalog();
    }
}
