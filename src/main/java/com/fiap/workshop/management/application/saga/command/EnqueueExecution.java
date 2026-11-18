package com.fiap.workshop.management.application.saga.command;

import java.util.List;
import java.util.UUID;

/** To Execution: queue the services of the paid order for the mechanics. */
public record EnqueueExecution(UUID sagaId, UUID serviceOrderId, List<Task> tasks) implements SagaCommand {

    public record Task(UUID taskId, UUID serviceId, String serviceName, String serviceType) {}
}
