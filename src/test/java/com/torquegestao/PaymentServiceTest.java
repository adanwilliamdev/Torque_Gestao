package com.torquegestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.torquegestao.domain.Client;
import com.torquegestao.domain.PaymentMethod;
import com.torquegestao.domain.PaymentStatus;
import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.domain.Vehicle;
import com.torquegestao.dto.PaymentRequest;
import com.torquegestao.dto.PaymentResponse;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.PaymentRepository;
import com.torquegestao.repository.ServiceOrderRepository;
import com.torquegestao.repository.UserRepository;
import com.torquegestao.service.PaymentService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Regras de recebimento isoladas do Spring e do banco (Mockito). */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock ServiceOrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock UserRepository users;

    PaymentService service;

    final UUID orderId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentService(orders, payments, users);
    }

    private ServiceOrder order(ServiceOrderStatus status, String total) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        ServiceOrder o = new ServiceOrder();
        o.setId(orderId);
        o.setClient(client);
        o.setVehicle(vehicle);
        o.setStatus(status);
        o.setLaborAmount(new BigDecimal(total));
        o.recalculateTotal();
        when(orders.findForUpdateById(orderId)).thenReturn(Optional.of(o));
        return o;
    }

    private static PaymentRequest pay(String amount) {
        return new PaymentRequest(new BigDecimal(amount), PaymentMethod.PIX, null);
    }

    @Test
    void pagamentoParcialDeixaAOsComoParcial() {
        ServiceOrder o = order(ServiceOrderStatus.FINALIZADA, "100.00");

        PaymentResponse response = service.register(orderId, pay("40.00"), userId);

        assertEquals(PaymentStatus.PARCIAL, o.getPaymentStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(o.getPaidAmount()));
        assertEquals(0, new BigDecimal("60.00").compareTo(o.getBalanceAmount()));
        assertEquals(0, new BigDecimal("40.00").compareTo(response.amount()));
        verify(payments).saveAndFlush(any());
    }

    @Test
    void pagamentosQueFechamOTotalQuitamAOs() {
        ServiceOrder o = order(ServiceOrderStatus.FINALIZADA, "100.00");

        service.register(orderId, pay("40.00"), userId);
        service.register(orderId, pay("60.00"), userId);

        assertEquals(PaymentStatus.PAGA, o.getPaymentStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(o.getBalanceAmount()));
    }

    @Test
    void naoAceitaValorAcimaDoSaldo() {
        ServiceOrder o = order(ServiceOrderStatus.FINALIZADA, "100.00");
        service.register(orderId, pay("70.00"), userId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(orderId, pay("40.00"), userId));

        assertEquals("O valor excede o saldo em aberto (R$ 30.00)", ex.getMessage());
        assertEquals(0, new BigDecimal("70.00").compareTo(o.getPaidAmount()));
    }

    @Test
    void osQuitadaNaoRecebeMaisPagamentos() {
        order(ServiceOrderStatus.FINALIZADA, "100.00");
        service.register(orderId, pay("100.00"), userId);

        assertThrows(BusinessException.class, () -> service.register(orderId, pay("1.00"), userId));
    }

    @Test
    void soOsFinalizadaRecebePagamento() {
        for (ServiceOrderStatus status : ServiceOrderStatus.values()) {
            if (status == ServiceOrderStatus.FINALIZADA) {
                continue;
            }
            order(status, "100.00");
            assertThrows(BusinessException.class, () -> service.register(orderId, pay("10.00"), userId));
        }
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void osInexistenteRetornaNaoEncontrada() {
        when(orders.findForUpdateById(orderId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.register(orderId, pay("10.00"), userId));
    }
}
