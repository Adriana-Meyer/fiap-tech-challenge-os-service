package com.fiap.workshop.management.infrastructure.messaging;

import com.fiap.workshop.management.application.saga.command.ConsumeStock;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnavailableSagaCommandPublisherTest {

    @Test
    void shouldFailLoudlyInsteadOfDroppingTheCommand() {
        UUID orderId = UUID.randomUUID();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new UnavailableSagaCommandPublisher().publish(new ConsumeStock(UUID.randomUUID(), orderId)));

        assertTrue(e.getMessage().contains("ConsumeStock"));
        assertTrue(e.getMessage().contains(orderId.toString()));
    }
}
