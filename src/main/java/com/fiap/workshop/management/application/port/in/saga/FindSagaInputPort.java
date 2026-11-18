package com.fiap.workshop.management.application.port.in.saga;

import com.fiap.workshop.management.application.dto.saga.SagaResponse;

import java.util.UUID;

public interface FindSagaInputPort {

    /** The most recent saga of the service order. */
    SagaResponse findLatestByServiceOrderId(UUID serviceOrderId);
}
