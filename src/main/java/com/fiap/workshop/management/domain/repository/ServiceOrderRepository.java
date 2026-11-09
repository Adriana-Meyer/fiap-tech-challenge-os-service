package com.fiap.workshop.management.domain.repository;

import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository {

    ServiceOrder save(ServiceOrder order);

    Optional<ServiceOrder> findById(UUID id);

    Optional<ServiceOrder> findByOsCode(String osCode);

    List<ServiceOrder> findAll();

    long countByYear(int year);
}
