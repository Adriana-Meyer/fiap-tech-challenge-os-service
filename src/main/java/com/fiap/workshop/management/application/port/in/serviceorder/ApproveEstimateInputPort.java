package com.fiap.workshop.management.application.port.in.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;

import java.util.UUID;

public interface ApproveEstimateInputPort {

    /** @param paymentMethod how the customer pays; the default method is used when null or blank */
    ServiceOrderResponse execute(UUID id, String paymentMethod);
}
