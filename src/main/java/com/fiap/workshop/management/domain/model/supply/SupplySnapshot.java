package com.fiap.workshop.management.domain.model.supply;

import com.fiap.workshop.management.domain.model.shared.Money;

import java.util.UUID;

/**
 * A supply as the Inventory service described it when it was added to an
 * order. The OS keeps this copy instead of the supply itself: the price and
 * name on the order must not change if the stock catalog changes later.
 */
public record SupplySnapshot(UUID id, String name, Money unitPrice) {}
