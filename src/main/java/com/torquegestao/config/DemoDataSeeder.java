package com.torquegestao.config;

import com.torquegestao.domain.Role;
import com.torquegestao.domain.PaymentMethod;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.domain.User;
import com.torquegestao.dto.BudgetRequest;
import com.torquegestao.dto.BudgetResponse;
import com.torquegestao.dto.ClientRequest;
import com.torquegestao.dto.MechanicRequest;
import com.torquegestao.dto.PartRequest;
import com.torquegestao.dto.PaymentRequest;
import com.torquegestao.dto.ServiceOrderItemRequest;
import com.torquegestao.dto.ServiceOrderRequest;
import com.torquegestao.dto.ServiceOrderResponse;
import com.torquegestao.dto.StatusUpdateRequest;
import com.torquegestao.dto.VehicleRequest;
import com.torquegestao.repository.ClientRepository;
import com.torquegestao.repository.UserRepository;
import com.torquegestao.security.AppUserDetails;
import com.torquegestao.service.AuthService;
import com.torquegestao.service.BudgetService;
import com.torquegestao.service.ClientService;
import com.torquegestao.service.InventoryService;
import com.torquegestao.service.MechanicService;
import com.torquegestao.service.PaymentService;
import com.torquegestao.service.ServiceOrderService;
import com.torquegestao.service.VehicleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Popula dados fictícios para demonstração e desenvolvimento ({@code app.seed.demo-data=true}).
 *
 * <p>Tudo passa pelos serviços da aplicação, então estoque, histórico de status, orçamentos e
 * pagamentos seguem as mesmas regras do uso real. Só as datas são recuadas depois (via SQL), para
 * que o dashboard tenha movimento ao longo das últimas semanas. Roda uma única vez: se já houver
 * qualquer cliente cadastrado, é ignorado. A semente é fixa, então o resultado é reproduzível.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final SecureRandom SECURE = new SecureRandom();

    private final AppProperties props;
    private final UserRepository users;
    private final ClientRepository clientRepository;
    private final AuthService authService;
    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final MechanicService mechanicService;
    private final InventoryService inventoryService;
    private final ServiceOrderService orderService;
    private final BudgetService budgetService;
    private final PaymentService paymentService;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(
            AppProperties props,
            UserRepository users,
            ClientRepository clientRepository,
            AuthService authService,
            ClientService clientService,
            VehicleService vehicleService,
            MechanicService mechanicService,
            InventoryService inventoryService,
            ServiceOrderService orderService,
            BudgetService budgetService,
            PaymentService paymentService,
            JdbcTemplate jdbc) {
        this.props = props;
        this.users = users;
        this.clientRepository = clientRepository;
        this.authService = authService;
        this.clientService = clientService;
        this.vehicleService = vehicleService;
        this.mechanicService = mechanicService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.budgetService = budgetService;
        this.paymentService = paymentService;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ dados-base

    private record ClientSeed(String name, String phone, String email, String address) {}

    private static final List<ClientSeed> CLIENTS = List.of(
            new ClientSeed("Carlos Eduardo Souza", "(38) 99812-3401", "carlos.souza@exemplo.com", "Rua das Acácias, 120 - Centro"),
            new ClientSeed("Mariana Ferreira Lima", "(38) 99745-8820", "mariana.lima@exemplo.com", "Av. Getúlio Vargas, 455 - Industrial"),
            new ClientSeed("João Pedro Almeida", "(38) 99633-1177", "joao.almeida@exemplo.com", "Rua Santos Dumont, 98 - Cidade Nova"),
            new ClientSeed("Ana Beatriz Rocha", "(38) 99921-4456", "ana.rocha@exemplo.com", "Rua Minas Gerais, 310 - Centro"),
            new ClientSeed("Roberto Carlos Nunes", "(38) 99580-7712", "roberto.nunes@exemplo.com", "Av. Antônio Carlos, 77 - Santo Antônio"),
            new ClientSeed("Fernanda Oliveira Costa", "(38) 99477-2290", "fernanda.costa@exemplo.com", "Rua do Comércio, 640 - Centro"),
            new ClientSeed("Paulo Henrique Martins", "(38) 99388-6035", "paulo.martins@exemplo.com", "Rua Bahia, 21 - Bela Vista"),
            new ClientSeed("Juliana Pereira Gomes", "(38) 99266-9148", "juliana.gomes@exemplo.com", "Av. São Francisco, 802 - São José"),
            new ClientSeed("Marcos Vinícius Barbosa", "(38) 99154-3367", "marcos.barbosa@exemplo.com", "Rua Tiradentes, 15 - Centro"),
            new ClientSeed("Luciana Azevedo Dias", "(38) 99043-5521", "luciana.dias@exemplo.com", "Rua Goiás, 289 - Jardim Primavera"),
            new ClientSeed("Transportadora Rio Velho Ltda", "(38) 3741-2200", "frota@rioveiho.exemplo.com", "Rod. BR-365, km 12 - Distrito Industrial"),
            new ClientSeed("Antônio Marcelo Teixeira", "(38) 98932-1084", "antonio.teixeira@exemplo.com", "Rua Pará, 530 - Várzea"));

    private record VehicleSeed(int clientIdx, String plate, String brand, String model, int year, int mileage, String fuel) {}

    private static final List<VehicleSeed> VEHICLES = List.of(
            new VehicleSeed(0, "QRA1B23", "Volkswagen", "Gol", 2016, 98_400, "Flex"),
            new VehicleSeed(0, "PXT4C56", "Fiat", "Strada", 2021, 41_200, "Flex"),
            new VehicleSeed(1, "RJK2D78", "Chevrolet", "Onix", 2020, 57_900, "Flex"),
            new VehicleSeed(2, "OPL9E01", "Toyota", "Hilux", 2018, 132_600, "Diesel"),
            new VehicleSeed(3, "QWE3F45", "Honda", "Civic", 2019, 72_300, "Gasolina"),
            new VehicleSeed(4, "RTY5G67", "Ford", "Ka", 2017, 88_100, "Flex"),
            new VehicleSeed(5, "PUI7H89", "Hyundai", "HB20", 2022, 23_500, "Flex"),
            new VehicleSeed(6, "RAS1J23", "Renault", "Duster", 2019, 69_800, "Flex"),
            new VehicleSeed(7, "QDF6K45", "Jeep", "Renegade", 2020, 51_000, "Flex"),
            new VehicleSeed(8, "OGH8L67", "Volkswagen", "Saveiro", 2015, 145_700, "Flex"),
            new VehicleSeed(9, "RZX4M89", "Fiat", "Argo", 2021, 34_800, "Flex"),
            new VehicleSeed(10, "PCV2N01", "Mercedes-Benz", "Accelo 1016", 2017, 310_400, "Diesel"),
            new VehicleSeed(10, "QBN5P23", "Volkswagen", "Delivery 11.180", 2019, 224_900, "Diesel"),
            new VehicleSeed(11, "RMK7Q45", "Nissan", "Kicks", 2023, 12_300, "Flex"),
            new VehicleSeed(3, "OTR3R67", "Honda", "CG 160", 2022, 18_700, "Flex"),
            new VehicleSeed(5, "PLM6S89", "Chevrolet", "S10", 2016, 176_200, "Diesel"));

    private record PartSeed(String code, String name, String manufacturer, String purchase, String sale, int stock, int min) {}

    // As 3 últimas ficam com estoque baixo de propósito (alerta de reposição) e não entram nas OS.
    private static final List<PartSeed> PARTS = List.of(
            new PartSeed("FLT-OLE-001", "Filtro de óleo", "Tecfil", "18.50", "35.00", 60, 10),
            new PartSeed("FLT-AR-002", "Filtro de ar", "Mann", "29.90", "58.00", 45, 8),
            new PartSeed("FLT-COM-003", "Filtro de combustível", "Fram", "34.00", "69.90", 38, 8),
            new PartSeed("FLT-CAB-004", "Filtro de cabine", "Tecfil", "22.00", "44.90", 40, 8),
            new PartSeed("OLE-5W30-005", "Óleo 5W30 sintético (1L)", "Mobil", "31.00", "58.90", 120, 20),
            new PartSeed("OLE-15W40-006", "Óleo 15W40 mineral (1L)", "Lubrax", "19.50", "36.90", 100, 20),
            new PartSeed("PAS-FRE-007", "Pastilha de freio dianteira", "Cobreq", "62.00", "129.90", 30, 6),
            new PartSeed("DIS-FRE-008", "Disco de freio (par)", "Fremax", "148.00", "289.00", 18, 4),
            new PartSeed("VEL-IGN-009", "Vela de ignição", "NGK", "14.90", "32.00", 80, 16),
            new PartSeed("COR-DEN-010", "Correia dentada", "Gates", "74.00", "149.90", 22, 5),
            new PartSeed("AMO-DIA-011", "Amortecedor dianteiro", "Monroe", "189.00", "359.00", 16, 4),
            new PartSeed("BAT-60A-012", "Bateria 60Ah", "Moura", "329.00", "519.00", 12, 3),
            new PartSeed("LAM-H4-013", "Lâmpada H4 (par)", "Philips", "27.00", "54.90", 35, 8),
            new PartSeed("FLU-ARR-014", "Fluido de arrefecimento (1L)", "Paraflu", "13.00", "27.90", 50, 10),
            new PartSeed("EMB-KIT-015", "Kit de embreagem", "LuK", "468.00", "829.00", 3, 4),
            new PartSeed("BOM-AGU-016", "Bomba d'água", "Urba", "96.00", "189.00", 2, 4),
            new PartSeed("SON-LAM-017", "Sonda lambda", "Bosch", "212.00", "399.00", 3, 5));

    /** Peças que podem entrar nas OS (as 3 últimas ficam de fora, com estoque baixo). */
    private static final int USABLE_PARTS = PARTS.size() - 3;

    private enum Outcome {
        CRIADA, EM_DIAGNOSTICO, ORCAMENTO_PENDENTE, ORCAMENTO_EXPIRADO, ORCAMENTO_RECUSADO,
        APROVADA, EM_EXECUCAO, FINALIZADA_SEM_PAGAMENTO, FINALIZADA_PARCIAL, FINALIZADA_PAGA, CANCELADA
    }

    private record OrderSeed(int vehicleIdx, int mechanicIdx, Outcome outcome, int daysAgo, String problem, String diagnosis) {}

    private static final List<OrderSeed> ORDERS = List.of(
            // concluídas e pagas (histórico das últimas semanas)
            new OrderSeed(0, 0, Outcome.FINALIZADA_PAGA, 52, "Troca de óleo e filtros na revisão dos 100 mil km", "Revisão preventiva completa"),
            new OrderSeed(2, 1, Outcome.FINALIZADA_PAGA, 47, "Barulho ao frear e pedal baixo", "Pastilhas e discos desgastados"),
            new OrderSeed(4, 2, Outcome.FINALIZADA_PAGA, 41, "Luz da bateria acendendo no painel", "Bateria no fim da vida útil"),
            new OrderSeed(7, 0, Outcome.FINALIZADA_PAGA, 36, "Motor superaquecendo no trânsito", "Bomba d'água com vazamento e fluido baixo"),
            new OrderSeed(8, 3, Outcome.FINALIZADA_PAGA, 30, "Revisão antes de viagem longa", "Revisão preventiva, velas trocadas"),
            new OrderSeed(10, 1, Outcome.FINALIZADA_PAGA, 26, "Troca de óleo e filtro de ar", "Manutenção periódica"),
            new OrderSeed(3, 2, Outcome.FINALIZADA_PAGA, 21, "Perda de potência em subidas", "Filtro de combustível entupido"),
            new OrderSeed(6, 0, Outcome.FINALIZADA_PAGA, 17, "Faróis fracos e lâmpada queimada", "Lâmpadas H4 substituídas"),
            new OrderSeed(13, 3, Outcome.FINALIZADA_PAGA, 12, "Primeira revisão (10 mil km)", "Revisão de garantia concluída"),
            // concluídas com cobrança em aberto
            new OrderSeed(11, 1, Outcome.FINALIZADA_PARCIAL, 15, "Caminhão com ruído na suspensão dianteira", "Amortecedores e buchas gastos"),
            new OrderSeed(12, 2, Outcome.FINALIZADA_PARCIAL, 9, "Troca de óleo da frota e filtros", "Manutenção da frota"),
            new OrderSeed(9, 0, Outcome.FINALIZADA_SEM_PAGAMENTO, 6, "Marcha arranhando e pedal duro", "Kit de embreagem substituído"),
            new OrderSeed(15, 3, Outcome.FINALIZADA_SEM_PAGAMENTO, 4, "Falha na partida a frio", "Velas e filtros substituídos"),
            // em andamento
            new OrderSeed(1, 1, Outcome.EM_EXECUCAO, 3, "Trepidação ao frear em alta velocidade", "Discos empenados, troca em andamento"),
            new OrderSeed(5, 2, Outcome.EM_EXECUCAO, 2, "Correia fazendo ruído e motor falhando", "Correia dentada no limite"),
            new OrderSeed(14, 3, Outcome.APROVADA, 2, "Moto sem força e consumo alto", "Filtro de ar e velas a trocar"),
            new OrderSeed(2, 0, Outcome.ORCAMENTO_PENDENTE, 1, "Amortecedor vazando óleo", "Amortecedor dianteiro com vazamento"),
            new OrderSeed(4, 1, Outcome.ORCAMENTO_PENDENTE, 1, "Carro puxando para o lado", "Amortecedores e alinhamento"),
            new OrderSeed(7, 2, Outcome.ORCAMENTO_EXPIRADO, 14, "Barulho na suspensão em lombadas", "Amortecedores com folga"),
            new OrderSeed(0, 3, Outcome.ORCAMENTO_RECUSADO, 10, "Check-engine aceso", "Sonda lambda com defeito"),
            new OrderSeed(8, 0, Outcome.EM_DIAGNOSTICO, 1, "Cheiro de queimado e fumaça leve", "Em análise"),
            new OrderSeed(10, 2, Outcome.EM_DIAGNOSTICO, 0, "Ar-condicionado não gela", "Aguardando teste de pressão"),
            new OrderSeed(6, 1, Outcome.CRIADA, 0, "Trocar óleo e verificar freios", null),
            new OrderSeed(13, 3, Outcome.CRIADA, 0, "Ruído estranho vindo do painel", null),
            // canceladas
            new OrderSeed(9, 2, Outcome.CANCELADA, 33, "Orçamento de retífica de motor", "Cliente desistiu do serviço"),
            new OrderSeed(15, 1, Outcome.CANCELADA, 19, "Troca de bateria", "Cliente já havia trocado em outra oficina"));

    // ------------------------------------------------------------------ execução

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = props.getSeed();
        if (!seed.isEnabled() || !seed.isDemoData()) {
            return;
        }
        String password = seed.getDemoPassword();
        boolean blank = password == null || password.isBlank();
        if (props.isProduction() && (blank || AppProperties.DEFAULT_ADMIN_PASSWORD.equals(password))) {
            log.warn("Dados de demonstração ignorados: defina SEED_DEMO_PASSWORD (forte) em produção.");
            return;
        }
        if (clientRepository.count() > 0) {
            log.info("Já existem clientes cadastrados, pulando dados de demonstração.");
            return;
        }
        User admin = users.findByEmail(seed.getAdminEmail()).orElse(null);
        if (admin == null) {
            log.warn("Dados de demonstração ignorados: usuário admin '{}' não encontrado.", seed.getAdminEmail());
            return;
        }
        if (blank) {
            password = randomPassword();
        }

        Random rnd = new Random(42);
        AppUserDetails actor = new AppUserDetails(admin);

        List<UUID> mechanicIds = seedTeam(password);
        List<UUID> clientIds = seedClients();
        List<UUID> vehicleIds = seedVehicles(clientIds);
        List<UUID> partIds = seedParts(admin.getId());
        int orders = seedOrders(actor, clientIds, vehicleIds, mechanicIds, partIds, rnd);

        log.info("Dados de demonstração criados: {} mecânicos, {} clientes, {} veículos, {} peças, {} OS.",
                mechanicIds.size(), clientIds.size(), vehicleIds.size(), partIds.size(), orders);
        if (blank) {
            log.warn("Senha das contas de demonstração (recepcao@, gerente@, mecanico1..3@torquegestao.com): {}", password);
        }
    }

    /** Contas de login e perfis de mecânico (3 com login, 2 só como cadastro). */
    private List<UUID> seedTeam(String password) {
        authService.createUser("Renata Recepção", "recepcao@torquegestao.com", password, Role.RECEPTIONIST);
        authService.createUser("Gilberto Gerente", "gerente@torquegestao.com", password, Role.MANAGER);

        String[][] mechanics = {
                {"Valdir Moreira", "Motor e injeção", "(38) 99111-2001", "mecanico1@torquegestao.com"},
                {"Sérgio Batista", "Freios e suspensão", "(38) 99111-2002", "mecanico2@torquegestao.com"},
                {"Edson Ribeiro", "Elétrica e diagnóstico", "(38) 99111-2003", "mecanico3@torquegestao.com"},
                {"Tiago Cardoso", "Revisões e troca de óleo", "(38) 99111-2004", null}};
        List<UUID> ids = new ArrayList<>();
        for (String[] m : mechanics) {
            UUID userId = m[3] == null
                    ? null
                    : authService.createUser(m[0], m[3], password, Role.MECHANIC).getId();
            ids.add(mechanicService.create(new MechanicRequest(m[0], m[1], m[2], userId)).id());
        }
        return ids;
    }

    private List<UUID> seedClients() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < CLIENTS.size(); i++) {
            ClientSeed c = CLIENTS.get(i);
            ids.add(clientService.create(
                    new ClientRequest(c.name(), cpf(i), c.phone(), c.email(), c.address())).id());
        }
        return ids;
    }

    private List<UUID> seedVehicles(List<UUID> clientIds) {
        List<UUID> ids = new ArrayList<>();
        for (VehicleSeed v : VEHICLES) {
            ids.add(vehicleService.create(new VehicleRequest(
                    v.plate(), v.brand(), v.model(), v.year(), v.mileage(), v.fuel(),
                    clientIds.get(v.clientIdx()))).id());
        }
        return ids;
    }

    private List<UUID> seedParts(UUID adminId) {
        List<UUID> ids = new ArrayList<>();
        for (PartSeed p : PARTS) {
            ids.add(inventoryService.create(new PartRequest(
                    p.name(), p.code(), p.manufacturer(), new BigDecimal(p.purchase()),
                    new BigDecimal(p.sale()), p.stock(), p.min()), adminId).id());
        }
        return ids;
    }

    private int seedOrders(
            AppUserDetails actor,
            List<UUID> clientIds,
            List<UUID> vehicleIds,
            List<UUID> mechanicIds,
            List<UUID> partIds,
            Random rnd) {
        int created = 0;
        for (OrderSeed s : ORDERS) {
            try {
                buildOrder(s, actor, clientIds, vehicleIds, mechanicIds, partIds, rnd);
                created++;
            } catch (RuntimeException e) {
                // Uma OS com problema não deve impedir o resto; o log diz qual foi.
                log.warn("Falha ao criar OS de demonstração ({} / {}): {}", s.problem(), s.outcome(), e.getMessage());
            }
        }
        return created;
    }

    /** Leva a OS pelo fluxo real até o estado desejado e, ao final, recua as datas. */
    private void buildOrder(
            OrderSeed s,
            AppUserDetails actor,
            List<UUID> clientIds,
            List<UUID> vehicleIds,
            List<UUID> mechanicIds,
            List<UUID> partIds,
            Random rnd) {
        VehicleSeed vehicle = VEHICLES.get(s.vehicleIdx());
        UUID clientId = clientIds.get(vehicle.clientIdx());
        UUID userId = actor.getId();

        ServiceOrderResponse order = orderService.create(new ServiceOrderRequest(
                clientId, vehicleIds.get(s.vehicleIdx()), mechanicIds.get(s.mechanicIdx()), s.problem()), userId);
        UUID id = order.id();
        Outcome outcome = s.outcome();

        if (outcome == Outcome.CRIADA) {
            backdate(order.id(), order.orderNumber(), s.daysAgo());
            return;
        }

        if (outcome == Outcome.CANCELADA) {
            // Cancelada depois de ter peças lançadas: o estoque é devolvido e fica no histórico.
            orderService.updateStatus(id, new StatusUpdateRequest(ServiceOrderStatus.EM_DIAGNOSTICO, s.diagnosis()), actor);
            addItems(id, partIds, actor, rnd, 1);
            orderService.updateStatus(id, new StatusUpdateRequest(ServiceOrderStatus.CANCELADA, null), actor);
            backdate(id, order.orderNumber(), s.daysAgo());
            return;
        }

        orderService.updateStatus(id, new StatusUpdateRequest(ServiceOrderStatus.EM_DIAGNOSTICO, s.diagnosis()), actor);
        if (outcome == Outcome.EM_DIAGNOSTICO) {
            backdate(id, order.orderNumber(), s.daysAgo());
            return;
        }

        // Peças e mão de obra antes do orçamento (o orçamento exige total > 0 e é igual ao total da OS).
        addItems(id, partIds, actor, rnd, 2 + rnd.nextInt(2));
        orderService.updateLaborAmount(id, labor(rnd), actor);
        orderService.updateStatus(id, new StatusUpdateRequest(ServiceOrderStatus.AGUARDANDO_APROVACAO, null), actor);

        BudgetResponse budget = budgetService.create(new BudgetRequest(
                clientId, vehicleIds.get(s.vehicleIdx()), id,
                "Orçamento: " + s.problem(), null, Instant.now().plus(7, ChronoUnit.DAYS)));

        switch (outcome) {
            case ORCAMENTO_PENDENTE -> { /* fica aguardando a decisão do cliente */ }
            case ORCAMENTO_EXPIRADO -> {
                // Não dá para criar um orçamento já vencido pela API; vence-se por SQL, como a rotina agendada faria.
                jdbc.update("UPDATE budgets SET status = 'EXPIRADO', valid_until = ? WHERE id = ?",
                        java.sql.Timestamp.from(Instant.now().minus(Math.max(1, s.daysAgo() - 7), ChronoUnit.DAYS)),
                        budget.id());
            }
            case ORCAMENTO_RECUSADO -> budgetService.reject(budget.id(), userId);
            default -> {
                budgetService.approve(budget.id(), userId);
                advanceAfterApproval(s, id, actor, order.orderNumber(), rnd);
            }
        }
        backdate(id, order.orderNumber(), s.daysAgo());
        jdbc.update("UPDATE budgets SET created_at = created_at - make_interval(days => ?),"
                + " updated_at = updated_at - make_interval(days => ?) WHERE service_order_id = ?",
                s.daysAgo(), s.daysAgo(), id);
    }

    private void advanceAfterApproval(OrderSeed s, UUID id, AppUserDetails actor, String number, Random rnd) {
        Outcome outcome = s.outcome();
        if (outcome == Outcome.APROVADA) {
            return;
        }
        orderService.updateStatus(id, new StatusUpdateRequest(ServiceOrderStatus.EM_EXECUCAO, null), actor);
        if (outcome == Outcome.EM_EXECUCAO) {
            return;
        }
        orderService.updateStatus(id, new StatusUpdateRequest(ServiceOrderStatus.FINALIZADA, null), actor);
        if (outcome == Outcome.FINALIZADA_SEM_PAGAMENTO) {
            return;
        }

        BigDecimal total = orderService.get(id, actor).totalAmount();
        PaymentMethod first = pickMethod(rnd);
        if (outcome == Outcome.FINALIZADA_PARCIAL) {
            BigDecimal half = total.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP);
            paymentService.register(id, new PaymentRequest(half, first, "Entrada"), actor.getId());
        } else if (rnd.nextBoolean()) {
            // Pago à vista em uma parcela só
            paymentService.register(id, new PaymentRequest(total, first, null), actor.getId());
        } else {
            // Pago em duas vezes (sinal + saldo)
            BigDecimal part = total.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
            paymentService.register(id, new PaymentRequest(part, first, "Sinal"), actor.getId());
            paymentService.register(id, new PaymentRequest(total.subtract(part), pickMethod(rnd), "Saldo na retirada"),
                    actor.getId());
        }
    }

    private void addItems(UUID orderId, List<UUID> partIds, AppUserDetails actor, Random rnd, int count) {
        List<Integer> used = new ArrayList<>();
        while (used.size() < count) {
            int idx = rnd.nextInt(USABLE_PARTS);
            if (used.contains(idx)) {
                continue;
            }
            used.add(idx);
            orderService.addItem(orderId, new ServiceOrderItemRequest(partIds.get(idx), 1 + rnd.nextInt(3)), actor);
        }
    }

    // ------------------------------------------------------------------ utilidades

    /**
     * Recua as datas da OS e de tudo que nasceu dela (histórico, pagamentos, movimentos de estoque).
     * Os serviços gravam sempre "agora"; sem isso o dashboard mostraria tudo no mesmo dia.
     */
    private void backdate(UUID orderId, String orderNumber, int days) {
        if (days <= 0) {
            return;
        }
        jdbc.update("UPDATE service_orders SET created_at = created_at - make_interval(days => ?),"
                + " updated_at = updated_at - make_interval(days => ?),"
                + " started_at = started_at - make_interval(days => ?),"
                + " completed_at = completed_at - make_interval(days => ?) WHERE id = ?",
                days, days, days, days, orderId);
        jdbc.update("UPDATE service_order_status_history SET changed_at = changed_at - make_interval(days => ?)"
                + " WHERE service_order_id = ?", days, orderId);
        jdbc.update("UPDATE payments SET paid_at = paid_at - make_interval(days => ?),"
                + " created_at = created_at - make_interval(days => ?) WHERE service_order_id = ?",
                days, days, orderId);
        jdbc.update("UPDATE inventory_movements SET created_at = created_at - make_interval(days => ?)"
                + " WHERE reference_id = ?", days, orderNumber);
    }

    private static BigDecimal labor(Random rnd) {
        int[] values = {80, 120, 150, 180, 220, 250, 300, 380, 450};
        return BigDecimal.valueOf(values[rnd.nextInt(values.length)]).setScale(2);
    }

    private static PaymentMethod pickMethod(Random rnd) {
        PaymentMethod[] all = PaymentMethod.values();
        return all[rnd.nextInt(all.length)];
    }

    /** CPF fictício com dígitos verificadores válidos, derivado do índice (nunca de uma pessoa real). */
    static String cpf(int index) {
        int[] d = new int[11];
        long base = 123_456_000L + index * 7_919L;
        for (int i = 8; i >= 0; i--) {
            d[i] = (int) (base % 10);
            base /= 10;
        }
        d[9] = checkDigit(d, 9);
        d[10] = checkDigit(d, 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            sb.append(d[i]);
            if (i == 2 || i == 5) {
                sb.append('.');
            } else if (i == 8) {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    private static int checkDigit(int[] d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += d[i] * (length + 1 - i);
        }
        int mod = (sum * 10) % 11;
        return mod == 10 ? 0 : mod;
    }

    private static String randomPassword() {
        byte[] bytes = new byte[15];
        SECURE.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
