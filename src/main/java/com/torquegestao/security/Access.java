package com.torquegestao.security;

/** Expressões de autorização por papel, reutilizadas na API REST e na interface web. */
public final class Access {

    private Access() {
    }

    public static final String ADMIN_ONLY = "hasRole('ADMIN')";

    public static final String CLIENT_READ = "hasAnyRole('ADMIN','RECEPTIONIST','MANAGER')";
    public static final String CLIENT_WRITE = "hasAnyRole('ADMIN','RECEPTIONIST')";

    public static final String VEHICLE_READ = "hasAnyRole('ADMIN','RECEPTIONIST','MANAGER','MECHANIC')";
    public static final String VEHICLE_WRITE = "hasAnyRole('ADMIN','RECEPTIONIST')";
    public static final String VEHICLE_MILEAGE = "hasAnyRole('ADMIN','RECEPTIONIST','MECHANIC')";

    public static final String MECHANIC_READ = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')";
    public static final String MECHANIC_MANAGE = "hasAnyRole('ADMIN','MANAGER')";

    public static final String STOCK = "hasAnyRole('ADMIN','MANAGER')";

    public static final String ORDER_STAFF = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')";
    public static final String ORDER_CREATE = "hasAnyRole('ADMIN','RECEPTIONIST')";
    public static final String ORDER_VIEW = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','MECHANIC')";
    public static final String ORDER_STATUS = "hasAnyRole('ADMIN','MANAGER','MECHANIC')";
    /** Ver o faturamento e lançar/remover peças usadas: o mecânico participa. */
    public static final String ORDER_BILLING = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','MECHANIC')";
    /** Valor da mão de obra é decisão comercial: o mecânico não altera. */
    public static final String ORDER_LABOR = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')";
    public static final String PAYMENT_READ = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')";
    public static final String PAYMENT_WRITE = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')";
    public static final String MECHANIC_ONLY = "hasRole('MECHANIC')";

    public static final String BUDGET_READ = "hasAnyRole('ADMIN','RECEPTIONIST','MANAGER')";
    public static final String BUDGET_CREATE = "hasAnyRole('ADMIN','RECEPTIONIST')";
    public static final String BUDGET_DECIDE = "hasAnyRole('ADMIN','MANAGER')";

    public static final String DASHBOARD = "hasAnyRole('ADMIN','MANAGER')";
}
