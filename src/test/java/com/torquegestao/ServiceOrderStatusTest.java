package com.torquegestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torquegestao.domain.ServiceOrderStatus;
import org.junit.jupiter.api.Test;

class ServiceOrderStatusTest {

    @Test
    void fluxoNormalAvancaUmPassoPorVez() {
        assertTrue(ServiceOrderStatus.CRIADA.canTransitionTo(ServiceOrderStatus.EM_DIAGNOSTICO));
        assertTrue(ServiceOrderStatus.EM_DIAGNOSTICO.canTransitionTo(ServiceOrderStatus.AGUARDANDO_APROVACAO));
        assertTrue(ServiceOrderStatus.AGUARDANDO_APROVACAO.canTransitionTo(ServiceOrderStatus.APROVADA));
        assertTrue(ServiceOrderStatus.APROVADA.canTransitionTo(ServiceOrderStatus.EM_EXECUCAO));
        assertTrue(ServiceOrderStatus.EM_EXECUCAO.canTransitionTo(ServiceOrderStatus.FINALIZADA));
    }

    @Test
    void naoPermitePularEtapas() {
        assertFalse(ServiceOrderStatus.CRIADA.canTransitionTo(ServiceOrderStatus.FINALIZADA));
        assertFalse(ServiceOrderStatus.CRIADA.canTransitionTo(ServiceOrderStatus.EM_EXECUCAO));
        assertFalse(ServiceOrderStatus.EM_DIAGNOSTICO.canTransitionTo(ServiceOrderStatus.APROVADA));
    }

    @Test
    void qualquerEtapaAbertaPodeSerCancelada() {
        for (ServiceOrderStatus status : ServiceOrderStatus.values()) {
            assertEquals(!status.isClosed(), status.canTransitionTo(ServiceOrderStatus.CANCELADA)
                    && status != ServiceOrderStatus.CANCELADA,
                    "cancelamento a partir de " + status);
        }
    }

    @Test
    void statusEncerradosNaoTemSaida() {
        assertTrue(ServiceOrderStatus.FINALIZADA.allowedNext().isEmpty());
        assertTrue(ServiceOrderStatus.CANCELADA.allowedNext().isEmpty());
        assertFalse(ServiceOrderStatus.FINALIZADA.canTransitionTo(ServiceOrderStatus.CRIADA));
    }

    @Test
    void repetirOMesmoStatusNaoEhUmaTransicao() {
        // Repetir o status reiniciaria carimbos (startedAt/completedAt); o service o recusa.
        for (ServiceOrderStatus status : ServiceOrderStatus.values()) {
            assertFalse(status.canTransitionTo(status));
        }
    }

    @Test
    void proximoPassoSegueOFluxoEEncerraNosTerminais() {
        assertEquals(ServiceOrderStatus.EM_DIAGNOSTICO, ServiceOrderStatus.CRIADA.nextStep());
        assertEquals(ServiceOrderStatus.FINALIZADA, ServiceOrderStatus.EM_EXECUCAO.nextStep());
        assertNull(ServiceOrderStatus.FINALIZADA.nextStep());
        assertNull(ServiceOrderStatus.CANCELADA.nextStep());
    }
}
