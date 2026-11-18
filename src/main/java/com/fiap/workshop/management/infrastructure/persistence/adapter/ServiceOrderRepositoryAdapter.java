package com.fiap.workshop.management.infrastructure.persistence.adapter;

import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import com.fiap.workshop.management.infrastructure.persistence.entity.ServiceOrderJpaEntity;
import com.fiap.workshop.management.infrastructure.persistence.mapper.ServiceOrderMapper;
import com.fiap.workshop.management.infrastructure.persistence.repository.ServiceOrderJpaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ServiceOrderRepositoryAdapter implements ServiceOrderRepository {

    private final ServiceOrderJpaRepository serviceOrderJpaRepository;
    private final EntityManager entityManager;
    private final ServiceOrderMapper mapper;

    public ServiceOrderRepositoryAdapter(ServiceOrderJpaRepository serviceOrderJpaRepository,
                                          EntityManager entityManager,
                                          ServiceOrderMapper mapper) {
        this.serviceOrderJpaRepository = serviceOrderJpaRepository;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public ServiceOrder save(ServiceOrder order) {
        ServiceOrderJpaEntity entity = mapper.toEntity(order, entityManager);
        return mapper.toDomain(serviceOrderJpaRepository.save(entity));
    }

    @Override
    public Optional<ServiceOrder> findById(UUID id) {
        return serviceOrderJpaRepository.findById(id.toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<ServiceOrder> findByOsCode(String osCode) {
        return serviceOrderJpaRepository.findByOsCode(osCode).map(mapper::toDomain);
    }

    @Override
    public List<ServiceOrder> findAll() {
        return serviceOrderJpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByYear(int year) {
        return serviceOrderJpaRepository.countByYear(year);
    }
}
