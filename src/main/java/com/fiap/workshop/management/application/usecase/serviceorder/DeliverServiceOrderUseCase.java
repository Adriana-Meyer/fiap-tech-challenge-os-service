package com.fiap.workshop.management.application.usecase.serviceorder;

import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.DeliverServiceOrderInputPort;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.shared.ElapsedTime;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import com.fiap.workshop.management.domain.service.MetricsPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeliverServiceOrderUseCase implements DeliverServiceOrderInputPort {

    private final ServiceOrderRepository serviceOrderRepository;
    private final MetricsPublisher metricsPublisher;

    public DeliverServiceOrderUseCase(ServiceOrderRepository serviceOrderRepository,
                                       MetricsPublisher metricsPublisher) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.metricsPublisher = metricsPublisher;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID id) {
        ServiceOrder order = serviceOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", id));
        order.deliver();
        metricsPublisher.recordServiceOrderStatusDuration(order.getId(), "FINALIZATION",
                ElapsedTime.minutesBetween(order.getExecutionFinishedAt(), order.getDeliveredAt()));
        return ServiceOrderResponse.from(serviceOrderRepository.save(order));
    }
}
