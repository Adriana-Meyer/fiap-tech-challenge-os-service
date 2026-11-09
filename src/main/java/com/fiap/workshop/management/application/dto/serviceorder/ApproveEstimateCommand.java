package com.fiap.workshop.management.application.dto.serviceorder;

/** Optional body of the approval: how the customer pays. */
public record ApproveEstimateCommand(String paymentMethod) {}
