package com.torquegestao.service;

import com.torquegestao.domain.Budget;
import com.torquegestao.domain.BudgetStatus;
import com.torquegestao.dto.BudgetRequest;
import com.torquegestao.dto.BudgetResponse;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.BudgetRepository;
import com.torquegestao.repository.ServiceOrderRepository;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgets;
    private final ServiceOrderRepository orders;
    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final ServiceOrderService serviceOrderService;

    public BudgetService(
            BudgetRepository budgets,
            ServiceOrderRepository orders,
            ClientService clientService,
            VehicleService vehicleService,
            ServiceOrderService serviceOrderService) {
        this.budgets = budgets;
        this.orders = orders;
        this.clientService = clientService;
        this.vehicleService = vehicleService;
        this.serviceOrderService = serviceOrderService;
    }

    public Budget getBudget(UUID id) {
        return budgets.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Orçamento não encontrado com ID: " + id));
    }

    @Transactional(readOnly = true)
    public BudgetResponse getResponse(UUID id) {
        return BudgetResponse.from(getBudget(id));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list() {
        return toResponses(budgets.findAllByOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> listByClient(UUID clientId) {
        return toResponses(budgets.findByClientIdOrderByCreatedAtDesc(clientId));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> listByStatus(BudgetStatus status) {
        return toResponses(budgets.findByStatusOrderByCreatedAtDesc(status));
    }

    public BudgetResponse create(BudgetRequest data) {
        var client = clientService.getClient(data.clientId());
        var vehicle = vehicleService.getVehicle(data.vehicleId());
        if (data.serviceOrderId() != null && !orders.existsById(data.serviceOrderId())) {
            throw new NotFoundException("Ordem de serviço não encontrada com ID: " + data.serviceOrderId());
        }

        Budget budget = new Budget();
        budget.setBudgetNumber(nextBudgetNumber());
        budget.setClient(client);
        budget.setVehicle(vehicle);
        budget.setServiceOrderId(data.serviceOrderId());
        budget.setDescription(Texts.blankToNull(data.description()));
        budget.setTotalAmount(data.totalAmount());
        budget.setValidUntil(data.validUntil());
        budget.setStatus(BudgetStatus.PENDENTE);
        budgets.saveAndFlush(budget);
        return BudgetResponse.from(budget);
    }

    public BudgetResponse approve(UUID id, UUID userId) {
        return decide(id, BudgetStatus.APROVADO, "aprovados", userId);
    }

    public BudgetResponse reject(UUID id, UUID userId) {
        return decide(id, BudgetStatus.RECUSADO, "recusados", userId);
    }

    /**
     * Decide o orçamento e propaga o resultado à OS vinculada (se houver), na mesma transação:
     * se a OS não puder reagir (ex.: não está aguardando aprovação), nada é alterado.
     */
    private BudgetResponse decide(UUID id, BudgetStatus newStatus, String verb, UUID userId) {
        Budget budget = getBudget(id);
        if (budget.getStatus() != BudgetStatus.PENDENTE) {
            throw new BusinessException("Apenas orçamentos pendentes podem ser " + verb);
        }
        if (budget.getServiceOrderId() != null) {
            serviceOrderService.applyBudgetDecision(
                    budget.getServiceOrderId(), newStatus == BudgetStatus.APROVADO, userId);
        }
        budget.setStatus(newStatus);
        budgets.saveAndFlush(budget);
        return BudgetResponse.from(budget);
    }

    private String nextBudgetNumber() {
        long seq = budgets.nextBudgetSequence();
        return String.format("BUD%d%06d", Year.now(ZoneOffset.UTC).getValue(), seq);
    }

    private static List<BudgetResponse> toResponses(List<Budget> list) {
        return list.stream().map(BudgetResponse::from).toList();
    }
}
