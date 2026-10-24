package com.torquegestao.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ServiceOrderStatus {
    CRIADA("Criada"),
    EM_DIAGNOSTICO("Em diagnóstico"),
    AGUARDANDO_APROVACAO("Aguardando aprovação"),
    APROVADA("Aprovada"),
    EM_EXECUCAO("Em execução"),
    FINALIZADA("Finalizada"),
    CANCELADA("Cancelada");

    private final String label;

    ServiceOrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Próximos status permitidos a partir deste. */
    public Set<ServiceOrderStatus> allowedNext() {
        return switch (this) {
            case CRIADA -> EnumSet.of(EM_DIAGNOSTICO, CANCELADA);
            case EM_DIAGNOSTICO -> EnumSet.of(AGUARDANDO_APROVACAO, CANCELADA);
            case AGUARDANDO_APROVACAO -> EnumSet.of(APROVADA, CANCELADA);
            case APROVADA -> EnumSet.of(EM_EXECUCAO, CANCELADA);
            case EM_EXECUCAO -> EnumSet.of(FINALIZADA, CANCELADA);
            case FINALIZADA, CANCELADA -> EnumSet.noneOf(ServiceOrderStatus.class);
        };
    }

    public boolean canTransitionTo(ServiceOrderStatus next) {
        return allowedNext().contains(next);
    }

    /** Próximo passo "natural" do fluxo (sem cancelamento), ou null se já está encerrada. */
    public ServiceOrderStatus nextStep() {
        return switch (this) {
            case CRIADA -> EM_DIAGNOSTICO;
            case EM_DIAGNOSTICO -> AGUARDANDO_APROVACAO;
            case AGUARDANDO_APROVACAO -> APROVADA;
            case APROVADA -> EM_EXECUCAO;
            case EM_EXECUCAO -> FINALIZADA;
            case FINALIZADA, CANCELADA -> null;
        };
    }

    public boolean isClosed() {
        return this == FINALIZADA || this == CANCELADA;
    }
}
