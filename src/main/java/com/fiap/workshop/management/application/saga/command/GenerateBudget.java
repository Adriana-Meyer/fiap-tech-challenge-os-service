package com.fiap.workshop.management.application.saga.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** To Billing: calculate the budget of the diagnosed order from a snapshot of its items. */
public record GenerateBudget(UUID sagaId, UUID serviceOrderId, List<Line> items) implements SagaCommand {

    public record Line(String description, BigDecimal unitPrice, int quantity) {}
}
