package com.fiap.workshop.management.application.saga;

import com.fiap.workshop.management.application.port.in.saga.HandleSagaEventInputPort;
import com.fiap.workshop.management.application.port.out.SagaCommandPublisher;
import com.fiap.workshop.management.application.saga.command.CancelBudget;
import com.fiap.workshop.management.application.saga.command.ConsumeStock;
import com.fiap.workshop.management.application.saga.command.EnqueueExecution;
import com.fiap.workshop.management.application.saga.command.GenerateBudget;
import com.fiap.workshop.management.application.saga.command.ProcessPayment;
import com.fiap.workshop.management.application.saga.command.RefundPayment;
import com.fiap.workshop.management.application.saga.command.ReleaseStock;
import com.fiap.workshop.management.application.saga.command.ReserveStock;
import com.fiap.workshop.management.application.saga.command.SagaCommand;
import com.fiap.workshop.management.application.saga.event.BudgetCancelled;
import com.fiap.workshop.management.application.saga.event.BudgetGenerated;
import com.fiap.workshop.management.application.saga.event.ExecutionCompleted;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueueFailed;
import com.fiap.workshop.management.application.saga.event.ExecutionEnqueued;
import com.fiap.workshop.management.application.saga.event.PaymentApproved;
import com.fiap.workshop.management.application.saga.event.PaymentDeclined;
import com.fiap.workshop.management.application.saga.event.PaymentRefunded;
import com.fiap.workshop.management.application.saga.event.SagaEvent;
import com.fiap.workshop.management.application.saga.event.StockConsumed;
import com.fiap.workshop.management.application.saga.event.StockReleased;
import com.fiap.workshop.management.application.saga.event.StockReservationFailed;
import com.fiap.workshop.management.application.saga.event.StockReserved;
import com.fiap.workshop.management.domain.exception.InvalidStatusTransitionException;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.model.customer.Customer;
import com.fiap.workshop.management.domain.model.saga.Compensation;
import com.fiap.workshop.management.domain.model.saga.SagaInstance;
import com.fiap.workshop.management.domain.model.saga.SagaStep;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrder;
import com.fiap.workshop.management.domain.model.serviceorder.ServiceOrderItem;
import com.fiap.workshop.management.domain.model.shared.Money;
import com.fiap.workshop.management.domain.repository.CustomerRepository;
import com.fiap.workshop.management.domain.repository.ProcessedMessageRepository;
import com.fiap.workshop.management.domain.repository.SagaInstanceRepository;
import com.fiap.workshop.management.domain.repository.ServiceOrderRepository;
import com.fiap.workshop.management.domain.service.EstimateNotificationService;
import com.fiap.workshop.management.domain.service.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the saga of a service order. The use cases of the order call
 * {@link #requestBudget}, {@link #approve} and {@link #reject}; everything the
 * other services answer comes back through {@link #handle}.
 *
 * <p>Every method runs in the caller's transaction and publishes its commands
 * before it commits. If publishing fails the transaction rolls back; if the
 * commit fails after a publish the command is out and the state is not (the
 * dual write a transactional outbox would remove, accepted for now). The
 * receivers are idempotent, so a retry of the same request is safe.
 */
@Service
public class SagaOrchestrator implements HandleSagaEventInputPort {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaInstanceRepository sagaRepository;
    private final ProcessedMessageRepository processedMessages;
    private final ServiceOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final SagaCommandPublisher publisher;
    private final EstimateNotificationService notificationService;
    private final MetricsPublisher metricsPublisher;

    public SagaOrchestrator(SagaInstanceRepository sagaRepository,
                            ProcessedMessageRepository processedMessages,
                            ServiceOrderRepository orderRepository,
                            CustomerRepository customerRepository,
                            SagaCommandPublisher publisher,
                            EstimateNotificationService notificationService,
                            MetricsPublisher metricsPublisher) {
        this.sagaRepository = sagaRepository;
        this.processedMessages = processedMessages;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.publisher = publisher;
        this.notificationService = notificationService;
        this.metricsPublisher = metricsPublisher;
    }

    // ---- started by the order's use cases --------------------------------

    /** Step 1: a diagnosed order asks Billing for its budget. */
    @Transactional
    public void requestBudget(ServiceOrder order) {
        sagaRepository.findLatestByServiceOrderId(order.getId()).filter(SagaInstance::isActive)
                .ifPresent(active -> {
                    throw new InvalidStatusTransitionException(
                            "Service order " + order.getOsCode() + " already has a saga in progress");
                });
        SagaInstance saga = sagaRepository.save(SagaInstance.start(order.getId()));
        publisher.publish(new GenerateBudget(saga.getId(), order.getId(), budgetLines(order)));
    }

    /** The customer approved: reserve the stock, which begins the fulfillment. */
    @Transactional
    public void approve(ServiceOrder order, String paymentMethod) {
        SagaInstance saga = awaitingApproval(order);
        saga.approved(paymentMethod);
        sagaRepository.save(saga);
        publisher.publish(new ReserveStock(saga.getId(), order.getId(), stockLines(order)));
    }

    /** The customer rejected the budget: the saga ends and the order goes back to diagnosis. */
    @Transactional
    public void reject(ServiceOrder order) {
        SagaInstance saga = awaitingApproval(order);
        saga.rejected();
        sagaRepository.save(saga);
    }

    // ---- answers from the other services ---------------------------------

    @Override
    @Transactional
    public void handle(SagaEvent event) {
        if (!processedMessages.markProcessed(event.messageId())) {
            log.info("Ignoring message {} already processed", event.messageId());
            return;
        }
        SagaInstance saga = sagaRepository.findByIdForUpdate(event.sagaId()).orElse(null);
        if (saga == null) {
            log.warn("Ignoring {} {}: saga {} does not exist", event.getClass().getSimpleName(),
                    event.messageId(), event.sagaId());
            return;
        }
        ServiceOrder order = orderRepository.findById(saga.getServiceOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("ServiceOrder", saga.getServiceOrderId()));
        dispatch(event, saga, order);
    }

    private void dispatch(SagaEvent event, SagaInstance saga, ServiceOrder order) {
        if (event instanceof BudgetGenerated e) {
            onBudgetGenerated(e, saga, order);
        } else if (event instanceof StockReserved e) {
            onStockReserved(e, saga, order);
        } else if (event instanceof StockReservationFailed e) {
            onStockReservationFailed(e, saga, order);
        } else if (event instanceof PaymentApproved e) {
            onPaymentApproved(e, saga, order);
        } else if (event instanceof PaymentDeclined e) {
            onPaymentDeclined(e, saga, order);
        } else if (event instanceof ExecutionEnqueued e) {
            onExecutionEnqueued(e, saga, order);
        } else if (event instanceof ExecutionEnqueueFailed e) {
            onExecutionEnqueueFailed(e, saga, order);
        } else if (event instanceof ExecutionCompleted e) {
            onExecutionCompleted(e, saga, order);
        } else if (event instanceof StockConsumed e) {
            onStockConsumed(e, saga);
        } else if (event instanceof StockReleased e) {
            onCompensationDone(e, saga, Compensation.RELEASE_STOCK);
        } else if (event instanceof BudgetCancelled e) {
            onCompensationDone(e, saga, Compensation.CANCEL_BUDGET);
        } else if (event instanceof PaymentRefunded e) {
            onCompensationDone(e, saga, Compensation.REFUND_PAYMENT);
        } else {
            log.warn("Ignoring unknown saga event {}", event.getClass().getName());
        }
    }

    private void onBudgetGenerated(BudgetGenerated event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.GENERATE_BUDGET)) {
            return;
        }
        saga.budgetGenerated();
        order.markBudgetReady(Money.of(event.totalAmount()));
        sagaRepository.save(saga);
        orderRepository.save(order);

        metricsPublisher.recordServiceOrderStatusDuration(order.getId(), "DIAGNOSIS",
                Duration.between(order.getDiagnosisStartedAt(), order.getWaitingApprovalAt()).toMinutes());
        Customer customer = customerRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", order.getCustomerId()));
        notificationService.notifyEstimateReady(order, customer);
    }

    private void onStockReserved(StockReserved event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.RESERVE_STOCK)) {
            return;
        }
        saga.stockReserved();
        sagaRepository.save(saga);
        publisher.publish(new ProcessPayment(saga.getId(), order.getId(), saga.getPaymentMethod()));
    }

    private void onStockReservationFailed(StockReservationFailed event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.RESERVE_STOCK)) {
            return;
        }
        compensate(saga, order, saga.stockReservationFailed(event.reason()));
    }

    private void onPaymentApproved(PaymentApproved event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.PROCESS_PAYMENT)) {
            return;
        }
        saga.paymentApproved();
        sagaRepository.save(saga);
        publisher.publish(new EnqueueExecution(saga.getId(), order.getId(), executionTasks(order)));
    }

    private void onPaymentDeclined(PaymentDeclined event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.PROCESS_PAYMENT)) {
            return;
        }
        compensate(saga, order, saga.paymentDeclined(event.reason()));
    }

    private void onExecutionEnqueued(ExecutionEnqueued event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.ENQUEUE_EXECUTION)) {
            return;
        }
        saga.executionEnqueued();
        order.startExecution();
        sagaRepository.save(saga);
        orderRepository.save(order);
    }

    private void onExecutionEnqueueFailed(ExecutionEnqueueFailed event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.ENQUEUE_EXECUTION)) {
            return;
        }
        compensate(saga, order, saga.executionEnqueueFailed(event.reason()));
    }

    private void onExecutionCompleted(ExecutionCompleted event, SagaInstance saga, ServiceOrder order) {
        if (!isExpected(event, saga, SagaStep.EXECUTING)) {
            return;
        }
        saga.executionCompleted();
        order.finishExecution();
        sagaRepository.save(saga);
        orderRepository.save(order);
        publisher.publish(new ConsumeStock(saga.getId(), order.getId()));
    }

    private void onStockConsumed(StockConsumed event, SagaInstance saga) {
        if (!isExpected(event, saga, SagaStep.CONSUME_STOCK)) {
            return;
        }
        saga.stockConsumed();
        sagaRepository.save(saga);
    }

    private void onCompensationDone(SagaEvent event, SagaInstance saga, Compensation compensation) {
        if (!saga.getPendingCompensations().contains(compensation)) {
            log.warn("Ignoring {} {}: saga {} is not waiting for {}", event.getClass().getSimpleName(),
                    event.messageId(), saga.getId(), compensation);
            return;
        }
        boolean compensated = saga.compensationDone(compensation);
        sagaRepository.save(saga);
        if (compensated) {
            log.info("Saga {} of service order {} is compensated", saga.getId(), saga.getServiceOrderId());
        }
    }

    // ---- helpers ----------------------------------------------------------

    /** The order is cancelled at once; the saga stays COMPENSATING until every undo is acknowledged. */
    private void compensate(SagaInstance saga, ServiceOrder order, Set<Compensation> compensations) {
        order.cancel();
        sagaRepository.save(saga);
        orderRepository.save(order);
        compensations.forEach(compensation -> publisher.publish(compensationCommand(compensation, saga)));
    }

    private SagaCommand compensationCommand(Compensation compensation, SagaInstance saga) {
        return switch (compensation) {
            case CANCEL_BUDGET -> new CancelBudget(saga.getId(), saga.getServiceOrderId());
            case RELEASE_STOCK -> new ReleaseStock(saga.getId(), saga.getServiceOrderId());
            case REFUND_PAYMENT -> new RefundPayment(saga.getId(), saga.getServiceOrderId());
        };
    }

    /** A message that does not fit the saga's current step is a late duplicate or a stray one: drop it. */
    private boolean isExpected(SagaEvent event, SagaInstance saga, SagaStep expected) {
        if (saga.isWaitingFor(expected)) {
            return true;
        }
        log.warn("Ignoring {} {}: saga {} is {} at {}, expected RUNNING at {}",
                event.getClass().getSimpleName(), event.messageId(), saga.getId(),
                saga.getStatus(), saga.getStep(), expected);
        return false;
    }

    private SagaInstance awaitingApproval(ServiceOrder order) {
        return sagaRepository.findLatestByServiceOrderId(order.getId())
                .filter(saga -> saga.isWaitingFor(SagaStep.AWAIT_APPROVAL))
                .orElseThrow(() -> new InvalidStatusTransitionException(
                        "Service order " + order.getOsCode() + " has no estimate awaiting a decision"));
    }

    private static List<GenerateBudget.Line> budgetLines(ServiceOrder order) {
        return order.getItems().stream()
                .map(item -> new GenerateBudget.Line(
                        item.getDescription(), item.getUnitPrice().getAmount(), item.getQuantity()))
                .toList();
    }

    private static List<ReserveStock.Line> stockLines(ServiceOrder order) {
        return order.getItems().stream()
                .filter(ServiceOrderItem::isSupply)
                .map(item -> new ReserveStock.Line(item.getSupplyId(), item.getQuantity()))
                .toList();
    }

    private static List<EnqueueExecution.Task> executionTasks(ServiceOrder order) {
        return order.getItems().stream()
                .filter(ServiceOrderItem::isService)
                .map(item -> new EnqueueExecution.Task(item.getId(), item.getService().getId(),
                        item.getService().getName(), item.getService().getType().name()))
                .toList();
    }
}
