package com.torquegestao.service;

import com.torquegestao.domain.Payment;
import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.dto.PaymentRequest;
import com.torquegestao.dto.PaymentResponse;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.PaymentRepository;
import com.torquegestao.repository.ServiceOrderRepository;
import com.torquegestao.repository.UserRepository;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recebimentos da OS. Só uma OS finalizada (total já travado) recebe pagamentos, e a soma deles
 * nunca passa do total. A linha da OS é travada durante o registro para que dois pagamentos
 * simultâneos não estourem o saldo.
 */
@Service
@Transactional
public class PaymentService {

    private final ServiceOrderRepository orders;
    private final PaymentRepository payments;
    private final UserRepository users;

    public PaymentService(ServiceOrderRepository orders, PaymentRepository payments, UserRepository users) {
        this.orders = orders;
        this.payments = payments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(UUID orderId) {
        if (!orders.existsById(orderId)) {
            throw new NotFoundException("Ordem de serviço não encontrada com ID: " + orderId);
        }
        return payments.findByServiceOrderIdOrderByPaidAtAsc(orderId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    public PaymentResponse register(UUID orderId, PaymentRequest data, UUID userId) {
        ServiceOrder order = orders.findForUpdateById(orderId)
                .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada com ID: " + orderId));
        if (order.getStatus() != ServiceOrderStatus.FINALIZADA) {
            throw new BusinessException("Pagamentos só podem ser registrados em ordens de serviço finalizadas");
        }
        var balance = order.getBalanceAmount();
        if (balance.signum() <= 0) {
            throw new BusinessException("Esta ordem de serviço já está quitada");
        }
        if (data.amount().compareTo(balance) > 0) {
            throw new BusinessException("O valor excede o saldo em aberto (R$ "
                    + balance.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")");
        }

        Payment payment = new Payment();
        payment.setServiceOrder(order);
        payment.setAmount(data.amount());
        payment.setMethod(data.method());
        payment.setNotes(Texts.blankToNull(data.notes()));
        payment.setPaidAt(Instant.now());
        payment.setCreatedBy(userId == null ? null : users.getReferenceById(userId));
        payments.saveAndFlush(payment);

        order.applyPayment(data.amount());
        orders.flush();
        return PaymentResponse.from(payment);
    }
}
