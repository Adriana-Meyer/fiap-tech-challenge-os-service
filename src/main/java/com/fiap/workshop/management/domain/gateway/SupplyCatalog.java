package com.fiap.workshop.management.domain.gateway;

import com.fiap.workshop.management.domain.model.supply.SupplySnapshot;

import java.util.Optional;
import java.util.UUID;

/** Port to the Inventory service, which owns the supplies. */
public interface SupplyCatalog {

    /**
     * @return the supply, or empty when Inventory does not know it
     * @throws com.fiap.workshop.management.domain.exception.SupplyCatalogUnavailableException
     *         when Inventory cannot be reached or answers with an error
     */
    Optional<SupplySnapshot> findById(UUID supplyId);
}
