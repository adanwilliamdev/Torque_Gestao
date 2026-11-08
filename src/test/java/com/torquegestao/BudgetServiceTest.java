package com.torquegestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.torquegestao.domain.Budget;
import com.torquegestao.domain.BudgetStatus;
import com.torquegestao.domain.Client;
import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.domain.Vehicle;
import com.torquegestao.dto.BudgetRequest;
import com.torquegestao.dto.BudgetResponse;
import com.torquegestao.exception.BudgetExpiredException;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.repository.BudgetRepository;
import com.torquegestao.service.BudgetService;
import com.torquegestao.service.ClientService;
import com.torquegestao.service.ServiceOrderService;
import com.torquegestao.service.VehicleService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Regras do orçamento isoladas do Spring e do banco (Mockito). */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock BudgetRepository budgets;
    @Mock ClientService clientService;
    @Mock VehicleService vehicleService;
    @Mock ServiceOrderService serviceOrderService;

    BudgetService service;

    final UUID userId = UUID.randomUUID();
    final UUID budgetId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();

    Client client;
    Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgets, clientService, vehicleService, serviceOrderService);
        client = new Client();
        client.setId(UUID.randomUUID());
        client.setName("Cliente");
        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setBrand("Fiat");
        vehicle.setModel("Uno");
        vehicle.setPlate("ABC1D23");
        vehicle.setClient(client);
    }

    // ------------------------------------------------------------------ helpers

    private Budget budget(BudgetStatus status, Instant validUntil, UUID serviceOrderId, String total) {
        Budget b = new Budget();
        b.setId(budgetId);
        b.setBudgetNumber("BUD2026000001");
        b.setClient(client);
        b.setVehicle(vehicle);
        b.setStatus(status);
        b.setValidUntil(validUntil);
        b.setServiceOrderId(serviceOrderId);
        b.setTotalAmount(new BigDecimal(total));
        when(budgets.findWithDetailsById(budgetId)).thenReturn(Optional.of(b));
        return b;
    }

    private ServiceOrder order(String total, ServiceOrderStatus status) {
        ServiceOrder o = new ServiceOrder();
        o.setId(orderId);
        o.setOrderNumber("OS2026000001");
        o.setClient(client);
        o.setVehicle(vehicle);
        o.setStatus(status);
        o.setLaborAmount(new BigDecimal(total));
        o.recalculateTotal();
        return o;
    }

    private void clientAndVehicleFound() {
        when(clientService.getActiveClient(client.getId())).thenReturn(client);
        when(vehicleService.getActiveVehicleOf(vehicle.getId(), client.getId())).thenReturn(vehicle);
    }

    private BudgetRequest request(UUID serviceOrderId, String amount, Instant validUntil) {
        return new BudgetRequest(
                client.getId(), vehicle.getId(), serviceOrderId, "Revisão",
                amount == null ? null : new BigDecimal(amount), validUntil);
    }

    // ------------------------------------------------------------------ expiração

    @Test
    void orcamentoVencidoNaoPodeSerAprovadoEFicaExpirado() {
        Budget b = budget(BudgetStatus.PENDENTE, Instant.now().minusSeconds(60), orderId, "300.00");

        assertThrows(BudgetExpiredException.class, () -> service.approve(budgetId, userId));

        assertEquals(BudgetStatus.EXPIRADO, b.getStatus());
        verify(budgets).saveAndFlush(b);
        verifyNoInteractions(serviceOrderService);
    }

    @Test
    void orcamentoVencidoTambemNaoPodeSerRecusado() {
        Budget b = budget(BudgetStatus.PENDENTE, Instant.now().minusSeconds(60), null, "300.00");

        assertThrows(BudgetExpiredException.class, () -> service.reject(budgetId, userId));

        assertEquals(BudgetStatus.EXPIRADO, b.getStatus());
    }

    @Test
    void orcamentoJaExpiradoNaoPodeSerDecidido() {
        budget(BudgetStatus.EXPIRADO, Instant.now().minusSeconds(60), null, "300.00");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(budgetId, userId));

        assertEquals("Orçamento expirado: crie um novo orçamento", ex.getMessage());
    }

    @Test
    void orcamentoDentroDaValidadeEhAprovado() {
        Budget b = budget(BudgetStatus.PENDENTE, Instant.now().plusSeconds(3600), null, "300.00");

        BudgetResponse response = service.approve(budgetId, userId);

        assertEquals(BudgetStatus.APROVADO, b.getStatus());
        assertEquals(BudgetStatus.APROVADO, response.status());
    }

    @Test
    void pendenteVencidoJaApareceComoExpiradoNaLeitura() {
        Budget b = budget(BudgetStatus.PENDENTE, Instant.now().minusSeconds(60), null, "300.00");

        assertEquals(BudgetStatus.EXPIRADO, BudgetResponse.from(b).status());
        assertEquals(BudgetStatus.PENDENTE, b.getStatus(), "a leitura não altera o registro");
    }

    @Test
    void rotinaDeExpiracaoDelegaAoRepositorio() {
        when(budgets.expireOverdue(any(Instant.class))).thenReturn(3);

        assertEquals(3, service.expireOverdue());
    }

    // ------------------------------------------------------------------ valor x OS

    @Test
    void aprovarNaoPassaSeOTotalDaOsMudouDepoisDoOrcamento() {
        budget(BudgetStatus.PENDENTE, null, orderId, "300.00");
        when(serviceOrderService.getOrder(orderId)).thenReturn(order("450.00", ServiceOrderStatus.AGUARDANDO_APROVACAO));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(budgetId, userId));

        assertEquals("O total da OS mudou (R$ 450.00) desde a criação do orçamento (R$ 300.00)."
                + " Atualize o valor do orçamento antes de aprovar", ex.getMessage());
        verify(serviceOrderService, never()).applyBudgetDecision(any(), anyBoolean(), any());
    }

    @Test
    void sincronizarAtualizaOValorParaOTotalDaOs() {
        Budget b = budget(BudgetStatus.PENDENTE, null, orderId, "300.00");
        when(serviceOrderService.getOrder(orderId)).thenReturn(order("450.00", ServiceOrderStatus.AGUARDANDO_APROVACAO));

        BudgetResponse response = service.syncWithOrder(budgetId);

        assertEquals(0, new BigDecimal("450.00").compareTo(b.getTotalAmount()));
        assertEquals(0, new BigDecimal("450.00").compareTo(response.totalAmount()));
    }

    @Test
    void orcamentoVinculadoUsaOTotalDaOs() {
        clientAndVehicleFound();
        when(serviceOrderService.getOrder(orderId)).thenReturn(order("450.00", ServiceOrderStatus.AGUARDANDO_APROVACAO));
        when(budgets.nextBudgetSequence()).thenReturn(1L);

        BudgetResponse response = service.create(request(orderId, null, null));

        assertEquals(0, new BigDecimal("450.00").compareTo(response.totalAmount()));
        assertEquals(orderId, response.serviceOrderId());
    }

    @Test
    void valorDivergenteDoTotalDaOsEhRecusado() {
        clientAndVehicleFound();
        when(serviceOrderService.getOrder(orderId)).thenReturn(order("450.00", ServiceOrderStatus.AGUARDANDO_APROVACAO));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(request(orderId, "300.00", null)));

        assertEquals("O valor informado (R$ 300.00) difere do total da OS (R$ 450.00)", ex.getMessage());
        verify(budgets, never()).saveAndFlush(any());
    }

    @Test
    void osSemValorLancadoNaoGeraOrcamento() {
        clientAndVehicleFound();
        when(serviceOrderService.getOrder(orderId)).thenReturn(order("0", ServiceOrderStatus.AGUARDANDO_APROVACAO));

        assertThrows(BusinessException.class, () -> service.create(request(orderId, null, null)));

        verify(budgets, never()).saveAndFlush(any());
    }

    @Test
    void orcamentoSemOsExigeValor() {
        clientAndVehicleFound();

        assertThrows(BusinessException.class, () -> service.create(request(null, null, null)));
    }

    // ------------------------------------------------------------------ consistência cliente/veículo/OS

    @Test
    void osDeOutroClienteOuVeiculoNaoPodeSerOrcada() {
        clientAndVehicleFound();
        ServiceOrder other = order("450.00", ServiceOrderStatus.AGUARDANDO_APROVACAO);
        Client outro = new Client();
        outro.setId(UUID.randomUUID());
        other.setClient(outro);
        when(serviceOrderService.getOrder(orderId)).thenReturn(other);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(request(orderId, null, null)));

        assertEquals("A OS OS2026000001 pertence a outro cliente ou veículo", ex.getMessage());
    }

    @Test
    void osEncerradaNaoPodeSerOrcada() {
        clientAndVehicleFound();
        when(serviceOrderService.getOrder(orderId)).thenReturn(order("450.00", ServiceOrderStatus.CANCELADA));

        assertThrows(BusinessException.class, () -> service.create(request(orderId, null, null)));
    }

    @Test
    void osComOrcamentoAtivoNaoRecebeOutro() {
        clientAndVehicleFound();
        when(serviceOrderService.getOrder(orderId)).thenReturn(order("450.00", ServiceOrderStatus.AGUARDANDO_APROVACAO));
        when(budgets.existsByServiceOrderIdAndStatusIn(eq(orderId), any())).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.create(request(orderId, null, null)));
    }

    @Test
    void validadeNoPassadoEhRecusada() {
        clientAndVehicleFound();

        assertThrows(BusinessException.class,
                () -> service.create(request(null, "100.00", Instant.now().minusSeconds(60))));
    }

    @Test
    void clienteInativoNaoRecebeOrcamento() {
        when(clientService.getActiveClient(client.getId())).thenThrow(new BusinessException("Cliente inativo: Cliente"));

        assertThrows(BusinessException.class, () -> service.create(request(null, "100.00", null)));

        verifyNoInteractions(vehicleService);
    }
}
