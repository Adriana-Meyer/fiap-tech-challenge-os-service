package com.fiap.workshop.management.interfaces.rest;

import com.fiap.workshop.management.application.dto.saga.SagaResponse;
import com.fiap.workshop.management.application.dto.serviceorder.AddItemCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ApproveEstimateCommand;
import com.fiap.workshop.management.application.dto.serviceorder.CompleteDiagnosisCommand;
import com.fiap.workshop.management.application.dto.serviceorder.CreateServiceOrderCommand;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderResponse;
import com.fiap.workshop.management.application.dto.serviceorder.ServiceOrderSummaryResponse;
import com.fiap.workshop.management.application.port.in.serviceorder.AddItemToServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.saga.FindSagaInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.ApproveEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.CompleteDiagnosisInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.CreateServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.DeliverServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.FindServiceOrderInputPort;
import com.fiap.workshop.management.application.dto.serviceorder.OpenFullServiceOrderCommand;
import com.fiap.workshop.management.application.port.in.serviceorder.OpenFullServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RejectEstimateInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.RemoveItemFromServiceOrderInputPort;
import com.fiap.workshop.management.application.port.in.serviceorder.StartDiagnosisInputPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-orders")
public class ServiceOrderController {

    private final CreateServiceOrderInputPort createUseCase;
    private final FindServiceOrderInputPort findUseCase;
    private final StartDiagnosisInputPort startDiagnosisUseCase;
    private final AddItemToServiceOrderInputPort addItemUseCase;
    private final RemoveItemFromServiceOrderInputPort removeItemUseCase;
    private final CompleteDiagnosisInputPort completeDiagnosisUseCase;
    private final ApproveEstimateInputPort approveEstimateUseCase;
    private final RejectEstimateInputPort rejectEstimateUseCase;
    private final DeliverServiceOrderInputPort deliverUseCase;
    private final OpenFullServiceOrderInputPort openFullUseCase;
    private final FindSagaInputPort findSagaUseCase;

    public ServiceOrderController(CreateServiceOrderInputPort createUseCase,
                                   FindServiceOrderInputPort findUseCase,
                                   StartDiagnosisInputPort startDiagnosisUseCase,
                                   AddItemToServiceOrderInputPort addItemUseCase,
                                   RemoveItemFromServiceOrderInputPort removeItemUseCase,
                                   CompleteDiagnosisInputPort completeDiagnosisUseCase,
                                   ApproveEstimateInputPort approveEstimateUseCase,
                                   RejectEstimateInputPort rejectEstimateUseCase,
                                   DeliverServiceOrderInputPort deliverUseCase,
                                   OpenFullServiceOrderInputPort openFullUseCase,
                                   FindSagaInputPort findSagaUseCase) {
        this.createUseCase = createUseCase;
        this.findUseCase = findUseCase;
        this.startDiagnosisUseCase = startDiagnosisUseCase;
        this.addItemUseCase = addItemUseCase;
        this.removeItemUseCase = removeItemUseCase;
        this.completeDiagnosisUseCase = completeDiagnosisUseCase;
        this.approveEstimateUseCase = approveEstimateUseCase;
        this.rejectEstimateUseCase = rejectEstimateUseCase;
        this.deliverUseCase = deliverUseCase;
        this.openFullUseCase = openFullUseCase;
        this.findSagaUseCase = findSagaUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOrderResponse create(@Valid @RequestBody CreateServiceOrderCommand command) {
        return createUseCase.execute(command);
    }

    @PostMapping("/full")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOrderResponse openFull(@Valid @RequestBody OpenFullServiceOrderCommand command) {
        return openFullUseCase.execute(command);
    }

    @GetMapping
    public List<ServiceOrderSummaryResponse> findAll(
            @RequestParam(defaultValue = "false") boolean includeCompleted) {
        return findUseCase.findAll(includeCompleted);
    }

    @GetMapping("/{id}")
    public ServiceOrderResponse findById(@PathVariable UUID id) {
        return findUseCase.findById(id);
    }

    @GetMapping("/{id}/saga")
    public SagaResponse findSaga(@PathVariable UUID id) {
        return findSagaUseCase.findLatestByServiceOrderId(id);
    }

    @PostMapping("/{id}/items")
    public ServiceOrderResponse addItem(@PathVariable UUID id,
                                         @Valid @RequestBody AddItemCommand command) {
        return addItemUseCase.execute(id, command);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ServiceOrderResponse removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        return removeItemUseCase.execute(id, itemId);
    }

    @PatchMapping("/{id}/diagnosis/start")
    public ServiceOrderResponse startDiagnosis(@PathVariable UUID id) {
        return startDiagnosisUseCase.execute(id);
    }

    @PostMapping("/{id}/diagnosis/complete")
    public ServiceOrderResponse completeDiagnosis(@PathVariable UUID id,
                                                   @Valid @RequestBody CompleteDiagnosisCommand command) {
        return completeDiagnosisUseCase.execute(id, command);
    }

    @PatchMapping("/{id}/estimate/approve")
    public ServiceOrderResponse approveEstimate(@PathVariable UUID id,
                                                @RequestBody(required = false) ApproveEstimateCommand command) {
        return approveEstimateUseCase.execute(id, command != null ? command.paymentMethod() : null);
    }

    @PatchMapping("/{id}/estimate/reject")
    public ServiceOrderResponse rejectEstimate(@PathVariable UUID id) {
        return rejectEstimateUseCase.execute(id);
    }

    @PatchMapping("/{id}/deliver")
    public ServiceOrderResponse deliver(@PathVariable UUID id) {
        return deliverUseCase.execute(id);
    }
}
