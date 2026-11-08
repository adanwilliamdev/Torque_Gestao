package com.torquegestao.service;

import com.torquegestao.domain.Budget;
import com.torquegestao.domain.BudgetStatus;
import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.dto.BudgetRequest;
import com.torquegestao.dto.BudgetResponse;
import com.torquegestao.exception.BudgetExpiredException;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.BudgetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BudgetService {

    /** Status em que o orçamento ainda "ocupa" a OS: só pode haver um por vez. */
    private static final List<BudgetStatus> ACTIVE_STATUSES = List.of(BudgetStatus.PENDENTE, BudgetStatus.APROVADO);

    private final BudgetRepository budgets;
    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final ServiceOrderService serviceOrderService;

    public BudgetService(
            BudgetRepository budgets,
            ClientService clientService,
            VehicleService vehicleService,
            ServiceOrderService serviceOrderService) {
        this.budgets = budgets;
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

    /**
     * Cria o orçamento. Cliente e veículo precisam estar ativos e o veículo ser do cliente. Quando há
     * OS vinculada, ela precisa ser do mesmo cliente e veículo, estar aberta e sem outro orçamento
     * ativo, e o valor do orçamento é o total da OS (um valor informado diferente é recusado).
     */
    public BudgetResponse create(BudgetRequest data) {
        var client = clientService.getActiveClient(data.clientId());
        var vehicle = vehicleService.getActiveVehicleOf(data.vehicleId(), client.getId());
        if (data.validUntil() != null && !data.validUntil().isAfter(Instant.now())) {
            throw new BusinessException("A validade do orçamento deve ser uma data futura");
        }

        BigDecimal amount = data.totalAmount();
        if (data.serviceOrderId() != null) {
            ServiceOrder order = serviceOrderService.getOrder(data.serviceOrderId());
            if (!order.getClient().getId().equals(client.getId())
                    || !order.getVehicle().getId().equals(vehicle.getId())) {
                throw new BusinessException("A OS " + order.getOrderNumber()
                        + " pertence a outro cliente ou veículo");
            }
            if (order.getStatus().isClosed()) {
                throw new BusinessException("Não é possível orçar uma OS finalizada ou cancelada");
            }
            if (budgets.existsByServiceOrderIdAndStatusIn(order.getId(), ACTIVE_STATUSES)) {
                throw new BusinessException("Já existe um orçamento ativo para a OS " + order.getOrderNumber());
            }
            BigDecimal orderTotal = order.getTotalAmount();
            if (orderTotal.signum() <= 0) {
                throw new BusinessException(
                        "A OS ainda não tem valor lançado (peças ou mão de obra) para gerar o orçamento");
            }
            if (amount != null && amount.compareTo(orderTotal) != 0) {
                throw new BusinessException("O valor informado (" + money(amount)
                        + ") difere do total da OS (" + money(orderTotal) + ")");
            }
            amount = orderTotal;
        } else if (amount == null) {
            throw new BusinessException("Informe o valor total do orçamento");
        }

        Budget budget = new Budget();
        budget.setBudgetNumber(nextBudgetNumber());
        budget.setClient(client);
        budget.setVehicle(vehicle);
        budget.setServiceOrderId(data.serviceOrderId());
        budget.setDescription(Texts.blankToNull(data.description()));
        budget.setTotalAmount(amount);
        budget.setValidUntil(data.validUntil());
        budget.setStatus(BudgetStatus.PENDENTE);
        budgets.saveAndFlush(budget);
        return BudgetResponse.from(budget);
    }

    /**
     * Atualiza o valor de um orçamento pendente para o total atual da OS vinculada. Serve para o caso
     * em que peças ou mão de obra mudaram depois do orçamento, sem precisar recusá-lo (o que cancelaria a OS).
     */
    @Transactional(noRollbackFor = BudgetExpiredException.class)
    public BudgetResponse syncWithOrder(UUID id) {
        Budget budget = getBudget(id);
        if (budget.getServiceOrderId() == null) {
            throw new BusinessException("Este orçamento não está vinculado a uma OS");
        }
        assertDecidable(budget);
        ServiceOrder order = serviceOrderService.getOrder(budget.getServiceOrderId());
        if (order.getTotalAmount().signum() <= 0) {
            throw new BusinessException("A OS não tem valor lançado para atualizar o orçamento");
        }
        budget.setTotalAmount(order.getTotalAmount());
        budgets.saveAndFlush(budget);
        return BudgetResponse.from(budget);
    }

    /** Marca como expirados os orçamentos pendentes vencidos (chamado pela rotina agendada). */
    public int expireOverdue() {
        return budgets.expireOverdue(Instant.now());
    }

    @Transactional(noRollbackFor = BudgetExpiredException.class)
    public BudgetResponse approve(UUID id, UUID userId) {
        return decide(id, BudgetStatus.APROVADO, "aprovados", userId);
    }

    @Transactional(noRollbackFor = BudgetExpiredException.class)
    public BudgetResponse reject(UUID id, UUID userId) {
        return decide(id, BudgetStatus.RECUSADO, "recusados", userId);
    }

    /**
     * Decide o orçamento e propaga o resultado à OS vinculada (se houver), na mesma transação:
     * se a OS não puder reagir (ex.: não está aguardando aprovação), nada é alterado.
     * Orçamento vencido não é decidido: é gravado como EXPIRADO e a decisão é recusada.
     */
    private BudgetResponse decide(UUID id, BudgetStatus newStatus, String verb, UUID userId) {
        Budget budget = getBudget(id);
        assertDecidable(budget, verb);
        if (budget.getServiceOrderId() != null) {
            if (newStatus == BudgetStatus.APROVADO) {
                assertTotalMatchesOrder(budget);
            }
            serviceOrderService.applyBudgetDecision(
                    budget.getServiceOrderId(), newStatus == BudgetStatus.APROVADO, userId);
        }
        budget.setStatus(newStatus);
        budgets.saveAndFlush(budget);
        return BudgetResponse.from(budget);
    }

    private void assertDecidable(Budget budget) {
        assertDecidable(budget, "alterados");
    }

    private void assertDecidable(Budget budget, String verb) {
        if (budget.getStatus() == BudgetStatus.EXPIRADO) {
            throw new BusinessException("Orçamento expirado: crie um novo orçamento");
        }
        if (budget.getStatus() != BudgetStatus.PENDENTE) {
            throw new BusinessException("Apenas orçamentos pendentes podem ser " + verb);
        }
        if (budget.isExpiredAt(Instant.now())) {
            budget.setStatus(BudgetStatus.EXPIRADO);
            budgets.saveAndFlush(budget);
            throw new BudgetExpiredException("Orçamento vencido: crie um novo orçamento");
        }
    }

    /** O cliente aprova um valor; se a OS mudou depois do orçamento, o valor aprovado deixou de valer. */
    private void assertTotalMatchesOrder(Budget budget) {
        ServiceOrder order = serviceOrderService.getOrder(budget.getServiceOrderId());
        if (order.getTotalAmount().compareTo(budget.getTotalAmount()) != 0) {
            throw new BusinessException("O total da OS mudou (" + money(order.getTotalAmount())
                    + ") desde a criação do orçamento (" + money(budget.getTotalAmount())
                    + "). Atualize o valor do orçamento antes de aprovar");
        }
    }

    private static String money(BigDecimal value) {
        return "R$ " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String nextBudgetNumber() {
        long seq = budgets.nextBudgetSequence();
        return String.format("BUD%d%06d", Year.now(ZoneOffset.UTC).getValue(), seq);
    }

    private static List<BudgetResponse> toResponses(List<Budget> list) {
        return list.stream().map(BudgetResponse::from).toList();
    }
}
