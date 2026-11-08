-- Regras de negócio: orçamento por OS, financeiro (pagamentos) e histórico de status.

-- 1) Orçamento: uma OS pode ter vários orçamentos ao longo do tempo (expirado/recusado),
--    mas somente um ativo (pendente ou aprovado) por vez.
ALTER TABLE budgets DROP CONSTRAINT uq_budgets_service_order_id;
CREATE UNIQUE INDEX uq_budgets_active_service_order
    ON budgets (service_order_id)
    WHERE service_order_id IS NOT NULL AND status IN ('PENDENTE', 'APROVADO');
CREATE INDEX ix_budgets_pending_valid_until
    ON budgets (valid_until)
    WHERE status = 'PENDENTE';

-- 2) Financeiro da OS: total pago e situação do pagamento.
ALTER TABLE service_orders
    ADD COLUMN paid_amount    numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_status varchar(20)   NOT NULL DEFAULT 'PENDENTE',
    ADD CONSTRAINT ck_service_orders_paid_amount CHECK (paid_amount >= 0),
    ADD CONSTRAINT ck_service_orders_payment_status
        CHECK (payment_status IN ('PENDENTE', 'PARCIAL', 'PAGA'));

-- OS finalizada sem nada a cobrar não tem saldo em aberto.
UPDATE service_orders SET payment_status = 'PAGA'
WHERE status = 'FINALIZADA' AND total_amount = 0;

CREATE TABLE payments (
    id                uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    service_order_id  uuid          NOT NULL REFERENCES service_orders (id),
    amount            numeric(12,2) NOT NULL,
    payment_method    varchar(20)   NOT NULL,
    paid_at           timestamptz   NOT NULL DEFAULT now(),
    notes             varchar(255),
    created_by        uuid          REFERENCES users (id) ON DELETE SET NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (payment_method IN
        ('DINHEIRO', 'PIX', 'CARTAO_DEBITO', 'CARTAO_CREDITO', 'TRANSFERENCIA'))
);
CREATE INDEX ix_payments_service_order_id ON payments (service_order_id);

-- 3) Histórico de status da OS: quem moveu, de onde para onde e quando.
CREATE TABLE service_order_status_history (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    service_order_id  uuid         NOT NULL REFERENCES service_orders (id) ON DELETE CASCADE,
    from_status       varchar(30),
    to_status         varchar(30)  NOT NULL,
    changed_by        uuid         REFERENCES users (id) ON DELETE SET NULL,
    changed_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_so_history_from CHECK (from_status IS NULL OR from_status IN
        ('CRIADA', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'APROVADA',
         'EM_EXECUCAO', 'FINALIZADA', 'CANCELADA')),
    CONSTRAINT ck_so_history_to CHECK (to_status IN
        ('CRIADA', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'APROVADA',
         'EM_EXECUCAO', 'FINALIZADA', 'CANCELADA'))
);
CREATE INDEX ix_so_history_order_changed_at
    ON service_order_status_history (service_order_id, changed_at);

-- Backfill das OS existentes: só se conhece a abertura e o status atual (autor desconhecido).
INSERT INTO service_order_status_history (service_order_id, from_status, to_status, changed_at)
SELECT id, NULL, 'CRIADA', created_at FROM service_orders;

INSERT INTO service_order_status_history (service_order_id, from_status, to_status, changed_at)
SELECT id, 'CRIADA', status, updated_at FROM service_orders WHERE status <> 'CRIADA';
