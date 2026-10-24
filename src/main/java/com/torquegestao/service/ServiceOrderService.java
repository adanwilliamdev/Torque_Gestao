package com.torquegestao.service;

import com.torquegestao.domain.Mechanic;
import com.torquegestao.domain.Part;
import com.torquegestao.domain.Role;
import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.domain.ServiceOrderItem;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.dto.PageResponse;
import com.torquegestao.dto.ServiceOrderItemRequest;
import com.torquegestao.dto.ServiceOrderRequest;
import com.torquegestao.dto.ServiceOrderResponse;
import com.torquegestao.dto.StatusUpdateRequest;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.ForbiddenException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.ServiceOrderRepository;
import com.torquegestao.security.AppUserDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceOrderService {

    private static final Set<ServiceOrderStatus> MECHANIC_ALLOWED_STATUSES = EnumSet.of(
            ServiceOrderStatus.EM_DIAGNOSTICO,
            ServiceOrderStatus.AGUARDANDO_APROVACAO,
            ServiceOrderStatus.EM_EXECUCAO,
            ServiceOrderStatus.FINALIZADA);

    private final ServiceOrderRepository orders;
    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final MechanicService mechanicService;
    private final InventoryService inventoryService;

    public ServiceOrderService(
            ServiceOrderRepository orders,
            ClientService clientService,
            VehicleService vehicleService,
            MechanicService mechanicService,
            InventoryService inventoryService) {
        this.orders = orders;
        this.clientService = clientService;
        this.vehicleService = vehicleService;
        this.mechanicService = mechanicService;
        this.inventoryService = inventoryService;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public ServiceOrderResponse get(UUID id, AppUserDetails user) {
        ServiceOrder order = getOrder(id);
        assertCanAccess(order, user);
        return ServiceOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> list() {
        return toResponses(orders.findAllByOrderByCreatedAtDesc());
    }

    /** Listagem paginada (opcionalmente filtrada por status). Ordena por createdAt desc por padrão. */
    @Transactional(readOnly = true)
    public PageResponse<ServiceOrderResponse> page(ServiceOrderStatus status, Pageable pageable) {
        Pageable safe = Pages.safe(
                pageable, Set.of("createdAt", "orderNumber", "status", "totalAmount"),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ServiceOrder> result = status == null ? orders.findAllBy(safe) : orders.findByStatus(status, safe);
        return PageResponse.from(result.map(ServiceOrderResponse::from));
    }

    /** Ordens atribuídas ao perfil de mecânico do usuário (vazio se ele não tiver perfil). */
    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> listForMechanic(AppUserDetails user) {
        return mechanicService.findByUserId(user.getId())
                .map(m -> toResponses(orders.findByMechanicIdOrderByCreatedAtDesc(m.getId())))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> listByClient(UUID clientId) {
        return toResponses(orders.findByClientIdOrderByCreatedAtDesc(clientId));
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> listByVehicle(UUID vehicleId) {
        return toResponses(orders.findByVehicleIdOrderByCreatedAtDesc(vehicleId));
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> listByStatus(ServiceOrderStatus status) {
        return toResponses(orders.findByStatusOrderByCreatedAtDesc(status));
    }

    /** Lista conforme o papel: MECHANIC vê só as suas; os demais veem todas. */
    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> listVisibleTo(AppUserDetails user) {
        return user.getRole() == Role.MECHANIC ? listForMechanic(user) : list();
    }

    // ---------------------------------------------------------------- comandos

    public ServiceOrderResponse create(ServiceOrderRequest data) {
        var client = clientService.getClient(data.clientId());
        var vehicle = vehicleService.getVehicle(data.vehicleId());
        Mechanic mechanic = data.mechanicId() == null ? null : mechanicService.getMechanic(data.mechanicId());

        ServiceOrder order = new ServiceOrder();
        order.setOrderNumber(nextOrderNumber());
        order.setClient(client);
        order.setVehicle(vehicle);
        order.setMechanic(mechanic);
        order.setReportedProblem(Texts.blankToNull(data.reportedProblem()));
        order.setStatus(ServiceOrderStatus.CRIADA);
        orders.saveAndFlush(order);
        return ServiceOrderResponse.from(order);
    }

    public ServiceOrderResponse updateStatus(UUID id, StatusUpdateRequest data, AppUserDetails user) {
        ServiceOrder order = getOrder(id);
        assertCanAccess(order, user);
        assertRoleCanSetStatus(user, data.status());
        changeStatus(order, data.status(), data.diagnosis(), user.getId());
        orders.flush();
        return ServiceOrderResponse.from(order);
    }

    /**
     * Reflete na OS vinculada a decisão sobre o orçamento: aprovar leva a OS para APROVADA; recusar
     * cancela a OS (devolvendo as peças ao estoque). Só vale enquanto a OS aguarda aprovação.
     */
    public void applyBudgetDecision(UUID orderId, boolean approved, UUID userId) {
        ServiceOrder order = getOrder(orderId);
        if (order.getStatus() != ServiceOrderStatus.AGUARDANDO_APROVACAO) {
            throw new BusinessException("A OS vinculada ao orçamento não está aguardando aprovação (status atual: "
                    + order.getStatus().getLabel() + ")");
        }
        changeStatus(order, approved ? ServiceOrderStatus.APROVADA : ServiceOrderStatus.CANCELADA, null, userId);
        orders.flush();
    }

    public ServiceOrderResponse assignMechanic(UUID id, UUID mechanicId) {
        ServiceOrder order = getOrder(id);
        assertEditable(order);
        order.setMechanic(mechanicService.getMechanic(mechanicId));
        orders.flush();
        return ServiceOrderResponse.from(order);
    }

    /**
     * Registra o uso de uma peça na OS: baixa o estoque (preço travado no momento do uso) e
     * recalcula o total faturado. A baixa de estoque já valida a disponibilidade.
     */
    public ServiceOrderResponse addItem(UUID id, ServiceOrderItemRequest data, AppUserDetails user) {
        ServiceOrder order = getOrder(id);
        assertCanAccess(order, user);
        assertEditable(order);

        Part part = inventoryService.getPart(data.partId());
        inventoryService.removeStock(
                part.getId(), data.quantity(), "Uso na OS " + order.getOrderNumber(), user.getId(),
                order.getOrderNumber());

        ServiceOrderItem item = new ServiceOrderItem();
        item.setServiceOrder(order);
        item.setPart(part);
        item.setQuantity(data.quantity());
        item.setUnitPrice(part.getSalePrice());
        order.getItems().add(item);

        order.recalculateTotal();
        orders.flush();
        return ServiceOrderResponse.from(order);
    }

    /** Remove uma peça lançada na OS e estorna a quantidade ao estoque. */
    public ServiceOrderResponse removeItem(UUID id, UUID itemId, AppUserDetails user) {
        ServiceOrder order = getOrder(id);
        assertCanAccess(order, user);
        assertEditable(order);

        ServiceOrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Item não encontrado na OS com ID: " + itemId));

        inventoryService.addStock(
                item.getPart().getId(), item.getQuantity(), "Estorno da OS " + order.getOrderNumber(),
                user.getId(), order.getOrderNumber());

        order.getItems().remove(item);
        order.recalculateTotal();
        orders.flush();
        return ServiceOrderResponse.from(order);
    }

    public ServiceOrderResponse updateLaborAmount(UUID id, BigDecimal laborAmount, AppUserDetails user) {
        ServiceOrder order = getOrder(id);
        assertCanAccess(order, user);
        assertEditable(order);

        order.setLaborAmount(laborAmount);
        order.recalculateTotal();
        orders.flush();
        return ServiceOrderResponse.from(order);
    }

    // ---------------------------------------------------------------- regras

    public ServiceOrder getOrder(UUID id) {
        return orders.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada com ID: " + id));
    }

    /** Um MECHANIC só enxerga/altera as ordens atribuídas ao seu próprio perfil de mecânico. */
    private void assertCanAccess(ServiceOrder order, AppUserDetails user) {
        if (user.getRole() != Role.MECHANIC) {
            return;
        }
        Mechanic mechanic = mechanicService.findByUserId(user.getId())
                .orElseThrow(() -> new ForbiddenException("Usuário não possui um perfil de mecânico vinculado"));
        if (order.getMechanic() == null || !order.getMechanic().getId().equals(mechanic.getId())) {
            throw new ForbiddenException("Esta ordem de serviço não está atribuída a você");
        }
    }

    /**
     * Único ponto onde o status muda. Garante o fluxo, preenche os carimbos de tempo uma só vez e,
     * ao cancelar, devolve ao estoque as peças já baixadas.
     */
    private void changeStatus(ServiceOrder order, ServiceOrderStatus next, String diagnosis, UUID userId) {
        ServiceOrderStatus current = order.getStatus();
        if (current == next) {
            // Reenviar o mesmo status só serve para ajustar o diagnóstico de uma OS ainda aberta;
            // nunca reinicia carimbos (startedAt/completedAt) nem repete efeitos colaterais.
            if (diagnosis != null && !current.isClosed()) {
                order.setDiagnosis(diagnosis);
                return;
            }
            throw new BusinessException("A ordem de serviço já está com o status " + current.getLabel());
        }
        validateTransition(current, next);

        order.setStatus(next);
        if (diagnosis != null) {
            order.setDiagnosis(diagnosis);
        }
        if (next == ServiceOrderStatus.EM_EXECUCAO) {
            order.setStartedAt(Instant.now());
        }
        if (next == ServiceOrderStatus.FINALIZADA) {
            order.setCompletedAt(Instant.now());
        }
        if (next == ServiceOrderStatus.CANCELADA) {
            restoreStock(order, userId);
        }
    }

    private void restoreStock(ServiceOrder order, UUID userId) {
        for (ServiceOrderItem item : order.getItems()) {
            inventoryService.addStock(
                    item.getPart().getId(), item.getQuantity(),
                    "Estorno por cancelamento da OS " + order.getOrderNumber(), userId, order.getOrderNumber());
        }
    }

    /** Mecânico executa o serviço, mas não aprova (decisão do cliente/gerência) nem cancela a OS. */
    private static void assertRoleCanSetStatus(AppUserDetails user, ServiceOrderStatus next) {
        if (user.getRole() == Role.MECHANIC && !MECHANIC_ALLOWED_STATUSES.contains(next)) {
            throw new ForbiddenException("Mecânicos não podem mover a OS para " + next.getLabel());
        }
    }

    private static void validateTransition(ServiceOrderStatus current, ServiceOrderStatus next) {
        if (!current.canTransitionTo(next)) {
            throw new BusinessException("Transição de status inválida de " + current + " para " + next);
        }
    }

    /** Peças e mão de obra só podem ser lançadas enquanto a OS ainda está em andamento. */
    private static void assertEditable(ServiceOrder order) {
        if (order.getStatus().isClosed()) {
            throw new BusinessException(
                    "Não é possível alterar peças ou mão de obra de uma OS finalizada ou cancelada");
        }
    }

    private String nextOrderNumber() {
        long seq = orders.nextOrderSequence();
        return String.format("OS%d%06d", java.time.Year.now(ZoneOffset.UTC).getValue(), seq);
    }

    private static List<ServiceOrderResponse> toResponses(List<ServiceOrder> list) {
        return list.stream().map(ServiceOrderResponse::from).toList();
    }
}
