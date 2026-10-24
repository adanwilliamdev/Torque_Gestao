package com.torquegestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.torquegestao.domain.Client;
import com.torquegestao.domain.Mechanic;
import com.torquegestao.domain.Part;
import com.torquegestao.domain.Role;
import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.domain.ServiceOrderItem;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.domain.User;
import com.torquegestao.domain.Vehicle;
import com.torquegestao.dto.StatusUpdateRequest;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.ForbiddenException;
import com.torquegestao.repository.ServiceOrderRepository;
import com.torquegestao.security.AppUserDetails;
import com.torquegestao.service.ClientService;
import com.torquegestao.service.InventoryService;
import com.torquegestao.service.MechanicService;
import com.torquegestao.service.ServiceOrderService;
import com.torquegestao.service.VehicleService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Regras da OS isoladas do Spring e do banco (Mockito). */
@ExtendWith(MockitoExtension.class)
class ServiceOrderServiceTest {

    @Mock ServiceOrderRepository orders;
    @Mock ClientService clientService;
    @Mock VehicleService vehicleService;
    @Mock MechanicService mechanicService;
    @Mock InventoryService inventory;

    ServiceOrderService service;

    final UUID orderId = UUID.randomUUID();
    final AppUserDetails admin = userWithRole(Role.ADMIN);

    @BeforeEach
    void setUp() {
        service = new ServiceOrderService(orders, clientService, vehicleService, mechanicService, inventory);
    }

    // ------------------------------------------------------------------ helpers

    private static AppUserDetails userWithRole(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Usuário " + role);
        user.setEmail(role.name().toLowerCase() + "@teste.com");
        user.setPasswordHash("x");
        user.setRole(role);
        user.setActive(true);
        return new AppUserDetails(user);
    }

    private ServiceOrder order(ServiceOrderStatus status) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setName("Cliente");
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setBrand("Fiat");
        vehicle.setModel("Uno");
        vehicle.setPlate("ABC1D23");

        ServiceOrder order = new ServiceOrder();
        order.setId(orderId);
        order.setOrderNumber("OS2026000001");
        order.setClient(client);
        order.setVehicle(vehicle);
        order.setStatus(status);
        when(orders.findWithDetailsById(orderId)).thenReturn(Optional.of(order));
        return order;
    }

    private static Part part(String code) {
        Part part = new Part();
        part.setId(UUID.randomUUID());
        part.setCode(code);
        part.setName("Peça " + code);
        part.setSalePrice(new java.math.BigDecimal("10.00"));
        return part;
    }

    private static ServiceOrderItem item(ServiceOrder order, Part part, int quantity) {
        ServiceOrderItem item = new ServiceOrderItem();
        item.setId(UUID.randomUUID());
        item.setServiceOrder(order);
        item.setPart(part);
        item.setQuantity(quantity);
        item.setUnitPrice(part.getSalePrice());
        order.getItems().add(item);
        return item;
    }

    // ------------------------------------------------------------------ cancelamento

    @Test
    void cancelarDevolveCadaPecaLancadaAoEstoque() {
        ServiceOrder order = order(ServiceOrderStatus.EM_EXECUCAO);
        Part a = part("A");
        Part b = part("B");
        item(order, a, 2);
        item(order, b, 5);

        service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.CANCELADA, null), admin);

        assertEquals(ServiceOrderStatus.CANCELADA, order.getStatus());
        verify(inventory).addStock(eq(a.getId()), eq(2), anyString(), eq(admin.getId()), eq("OS2026000001"));
        verify(inventory).addStock(eq(b.getId()), eq(5), anyString(), eq(admin.getId()), eq("OS2026000001"));
    }

    @Test
    void cancelarOsSemPecasNaoMexeNoEstoque() {
        order(ServiceOrderStatus.CRIADA);

        service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.CANCELADA, null), admin);

        verifyNoInteractions(inventory);
    }

    // ------------------------------------------------------------------ transições

    @Test
    void repetirOMesmoStatusEhRecusadoESemEfeitosColaterais() {
        ServiceOrder order = order(ServiceOrderStatus.FINALIZADA);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.FINALIZADA, null), admin));

        assertEquals("A ordem de serviço já está com o status Finalizada", ex.getMessage());
        assertNull(order.getCompletedAt(), "completedAt não pode ser sobrescrito");
        verifyNoInteractions(inventory);
    }

    @Test
    void mesmoStatusComDiagnosticoAtualizaApenasODiagnosticoDeOsAberta() {
        ServiceOrder order = order(ServiceOrderStatus.EM_DIAGNOSTICO);

        service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.EM_DIAGNOSTICO, "Correia gasta"), admin);

        assertEquals("Correia gasta", order.getDiagnosis());
        assertEquals(ServiceOrderStatus.EM_DIAGNOSTICO, order.getStatus());
    }

    @Test
    void iniciarExecucaoERegistrarFinalizacaoPreenchemOsCarimbos() {
        ServiceOrder order = order(ServiceOrderStatus.APROVADA);

        service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.EM_EXECUCAO, null), admin);
        assertNotNull(order.getStartedAt());

        service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.FINALIZADA, null), admin);
        assertNotNull(order.getCompletedAt());
    }

    // ------------------------------------------------------------------ papel do mecânico

    private AppUserDetails mechanicAssignedTo(ServiceOrder order) {
        AppUserDetails user = userWithRole(Role.MECHANIC);
        Mechanic mechanic = new Mechanic();
        mechanic.setId(UUID.randomUUID());
        order.setMechanic(mechanic);
        when(mechanicService.findByUserId(user.getId())).thenReturn(Optional.of(mechanic));
        return user;
    }

    @Test
    void mecanicoNaoPodeAprovarNemCancelarAOs() {
        ServiceOrder order = order(ServiceOrderStatus.AGUARDANDO_APROVACAO);
        AppUserDetails mechanic = mechanicAssignedTo(order);

        assertThrows(ForbiddenException.class, () ->
                service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.APROVADA, null), mechanic));
        assertThrows(ForbiddenException.class, () ->
                service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.CANCELADA, null), mechanic));

        assertEquals(ServiceOrderStatus.AGUARDANDO_APROVACAO, order.getStatus());
        verifyNoInteractions(inventory);
    }

    @Test
    void mecanicoPodeExecutarEFinalizarOServico() {
        ServiceOrder order = order(ServiceOrderStatus.APROVADA);
        AppUserDetails mechanic = mechanicAssignedTo(order);

        service.updateStatus(orderId, new StatusUpdateRequest(ServiceOrderStatus.EM_EXECUCAO, null), mechanic);

        assertEquals(ServiceOrderStatus.EM_EXECUCAO, order.getStatus());
    }

    // ------------------------------------------------------------------ mecânico atribuído

    @Test
    void naoSeAtribuiMecanicoAOsEncerrada() {
        order(ServiceOrderStatus.FINALIZADA);

        assertThrows(BusinessException.class, () -> service.assignMechanic(orderId, UUID.randomUUID()));

        verify(mechanicService, never()).getMechanic(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------ decisão do orçamento

    @Test
    void orcamentoAprovadoLevaAOsParaAprovada() {
        ServiceOrder order = order(ServiceOrderStatus.AGUARDANDO_APROVACAO);

        service.applyBudgetDecision(orderId, true, admin.getId());

        assertEquals(ServiceOrderStatus.APROVADA, order.getStatus());
    }

    @Test
    void orcamentoRecusadoCancelaAOsEDevolveAsPecas() {
        ServiceOrder order = order(ServiceOrderStatus.AGUARDANDO_APROVACAO);
        Part a = part("A");
        item(order, a, 3);

        service.applyBudgetDecision(orderId, false, admin.getId());

        assertEquals(ServiceOrderStatus.CANCELADA, order.getStatus());
        verify(inventory).addStock(eq(a.getId()), eq(3), anyString(), eq(admin.getId()), eq("OS2026000001"));
    }

    @Test
    void decisaoDeOrcamentoSoValeComAOsAguardandoAprovacao() {
        ServiceOrder order = order(ServiceOrderStatus.EM_DIAGNOSTICO);

        assertThrows(BusinessException.class, () -> service.applyBudgetDecision(orderId, true, admin.getId()));

        assertEquals(ServiceOrderStatus.EM_DIAGNOSTICO, order.getStatus());
    }
}
