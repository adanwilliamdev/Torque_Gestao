package com.torquegestao;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.torquegestao.domain.Budget;
import com.torquegestao.domain.BudgetStatus;
import com.torquegestao.domain.Client;
import com.torquegestao.domain.Role;
import com.torquegestao.domain.User;
import com.torquegestao.repository.BudgetRepository;
import com.torquegestao.repository.ClientRepository;
import com.torquegestao.repository.UserRepository;
import com.torquegestao.service.BudgetService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class ApiFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired BudgetRepository budgetRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired BudgetService budgetService;

    // ------------------------------------------------------------------ helpers

    private String email(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@teste.com";
    }

    private User createUser(Role role) {
        User user = new User();
        user.setName("Usuário " + role);
        user.setEmail(email(role.name().toLowerCase()));
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return users.saveAndFlush(user);
    }

    private String tokenFor(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private String adminToken() throws Exception {
        return tokenFor(createUser(Role.ADMIN));
    }

    private ResultActions send(String method, String url, String token, Object body) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PATCH" -> patch(url);
            case "DELETE" -> delete(url);
            default -> get(url);
        };
        builder.header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    private JsonNode created(String url, String token, Object body) throws Exception {
        String content = send("POST", url, token, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    private String uniquePlate() {
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append((char) ('A' + rnd.nextInt(26)));
        }
        sb.append(rnd.nextInt(10)).append((char) ('A' + rnd.nextInt(26)))
                .append(rnd.nextInt(10)).append(rnd.nextInt(10));
        return sb.toString();
    }

    /** Compara valores monetários ignorando a escala (0 == 0.00 == 0.0). */
    private static org.hamcrest.Matcher<Object> amount(String expected) {
        return new org.hamcrest.TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Object actual) {
                return new java.math.BigDecimal(actual.toString()).compareTo(new java.math.BigDecimal(expected)) == 0;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("valor igual a ").appendText(expected);
            }
        };
    }

    // ------------------------------------------------------------------ autenticação

    @Test
    void loginComCredenciaisValidasDevolveTokens() throws Exception {
        User user = createUser(Role.ADMIN);
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        User user = createUser(Role.ADMIN);
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", "errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Email ou senha inválidos")));
    }

    @Test
    void refreshTokenRodaERecusaReuso() throws Exception {
        User user = createUser(Role.ADMIN);
        String loginBody = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String refresh = json.readTree(loginBody).get("refreshToken").asText();
        String payload = json.writeValueAsString(Map.of("refreshToken", refresh));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autocadastroPublicoNaoExiste() throws Exception {
        // Sistema interno: contas só são criadas por um ADMIN (POST /api/auth/users).
        String mail = email("intruso");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "X", "email", mail, "password", "senha123"))))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertTrue(users.findByEmail(mail).isEmpty());
    }

    @Test
    void somenteAdminCriaUsuarios() throws Exception {
        Map<String, String> body = Map.of("name", "Novo", "email", email("novo"), "password", "senha123", "role", "MANAGER");
        send("POST", "/api/auth/users", tokenFor(createUser(Role.RECEPTIONIST)), body).andExpect(status().isForbidden());
        send("POST", "/api/auth/users", adminToken(), body).andExpect(status().isCreated());
    }

    @Test
    void semTokenRetorna401EmJson() throws Exception {
        mvc.perform(get("/api/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mvc.perform(get("/api/clients").header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Token inválido ou expirado")));
    }

    // ------------------------------------------------------------------ RBAC

    @Test
    void recepcionistaNaoCriaPecaMasCriaCliente() throws Exception {
        String token = tokenFor(createUser(Role.RECEPTIONIST));
        Map<String, Object> part = Map.of("name", "Filtro", "code", "F-" + UUID.randomUUID(),
                "purchasePrice", 10, "salePrice", 20, "stockQuantity", 5);
        send("POST", "/api/inventory/parts", token, part)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Você não tem permissão para executar esta ação")));
        send("POST", "/api/clients", token, Map.of("name", "Maria")).andExpect(status().isCreated());
    }

    @Test
    void gerenteNaoCriaClienteMasLista() throws Exception {
        String token = tokenFor(createUser(Role.MANAGER));
        send("POST", "/api/clients", token, Map.of("name", "Maria")).andExpect(status().isForbidden());
        send("GET", "/api/clients", token, null).andExpect(status().isOk());
    }

    @Test
    void mecanicoSemPerfilNaoEnxergaOrdens() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        String mecanico = tokenFor(createUser(Role.MECHANIC));

        send("GET", "/api/service-orders", mecanico, null).andExpect(status().isForbidden());
        send("GET", "/api/service-orders/" + order.get("id").asText(), mecanico, null)
                .andExpect(status().isForbidden());
        send("GET", "/api/service-orders/mine", mecanico, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // ------------------------------------------------------------------ validação

    @Test
    void validacaoRetorna422ComListaDeErros() throws Exception {
        String admin = adminToken();
        send("POST", "/api/clients", admin, Map.of("name", ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors", hasItem("name: Nome é obrigatório")));
    }

    @Test
    void placaInvalidaRetorna422EDuplicadaRetorna400() throws Exception {
        String admin = adminToken();
        JsonNode client = created("/api/clients", admin, Map.of("name", "Cliente Placa"));
        Map<String, Object> bad = Map.of("plate", "XXXX", "brand", "VW", "model", "Gol", "year", 2010,
                "clientId", client.get("id").asText());
        send("POST", "/api/vehicles", admin, bad).andExpect(status().isUnprocessableEntity());

        String plate = uniquePlate();
        Map<String, Object> ok = Map.of("plate", plate.toLowerCase(), "brand", "VW", "model", "Gol",
                "year", 2010, "clientId", client.get("id").asText());
        send("POST", "/api/vehicles", admin, ok)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate", is(plate)));
        send("POST", "/api/vehicles", admin, ok)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Já existe um veículo com esta placa: " + plate)));
    }

    @Test
    void cpfDuplicadoRetorna409() throws Exception {
        String admin = adminToken();
        String cpf = String.valueOf(System.nanoTime()).substring(0, 11);
        created("/api/clients", admin, Map.of("name", "A", "cpf", cpf));
        send("POST", "/api/clients", admin, Map.of("name", "B", "cpf", cpf))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ fluxo de faturamento

    private JsonNode novaOrdem(String token, String mechanicId) throws Exception {
        JsonNode client = created("/api/clients", token, Map.of("name", "Cliente OS"));
        JsonNode vehicle = created("/api/vehicles", token, Map.of("plate", uniquePlate(), "brand", "Fiat",
                "model", "Uno", "year", 2012, "clientId", client.get("id").asText()));
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("clientId", client.get("id").asText());
        body.put("vehicleId", vehicle.get("id").asText());
        body.put("reportedProblem", "Barulho no motor");
        if (mechanicId != null) {
            body.put("mechanicId", mechanicId);
        }
        return created("/api/service-orders", token, body);
    }

    private JsonNode novaPeca(String token, int stock, String price) throws Exception {
        return created("/api/inventory/parts", token, Map.of("name", "Pastilha", "code", "P-" + UUID.randomUUID(),
                "purchasePrice", "10.00", "salePrice", price, "stockQuantity", stock));
    }

    @Test
    void faturamentoBaixaEstoqueSomaTotalEEstornaNaRemocao() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        JsonNode part = novaPeca(admin, 10, "50.00");
        String orderId = order.get("id").asText();
        String partId = part.get("id").asText();

        org.junit.jupiter.api.Assertions.assertTrue(order.get("orderNumber").asText().matches("OS\\d{4}\\d{6}"));

        JsonNode withItem = json.readTree(send("POST", "/api/service-orders/" + orderId + "/items", admin,
                        Map.of("partId", partId, "quantity", 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partsAmount", amount("150.00")))
                .andExpect(jsonPath("$.totalAmount", amount("150.00")))
                .andExpect(jsonPath("$.items.length()", is(1)))
                .andReturn().getResponse().getContentAsString());
        String itemId = withItem.get("items").get(0).get("id").asText();

        send("GET", "/api/inventory/parts/" + partId, admin, null)
                .andExpect(jsonPath("$.stockQuantity", is(7)));

        send("PATCH", "/api/service-orders/" + orderId + "/labor", admin, Map.of("laborAmount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laborAmount", amount("100.00")))
                .andExpect(jsonPath("$.totalAmount", amount("250.00")));

        send("DELETE", "/api/service-orders/" + orderId + "/items/" + itemId, admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partsAmount", amount("0")))
                .andExpect(jsonPath("$.totalAmount", amount("100.00")));

        send("GET", "/api/inventory/parts/" + partId, admin, null)
                .andExpect(jsonPath("$.stockQuantity", is(10)));
        send("GET", "/api/inventory/parts/" + partId + "/movements", admin, null)
                .andExpect(jsonPath("$.length()", is(3)));
    }

    @Test
    void estoqueInsuficienteRetorna400SemAlterarSaldo() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        JsonNode part = novaPeca(admin, 2, "30.00");

        send("POST", "/api/service-orders/" + order.get("id").asText() + "/items", admin,
                        Map.of("partId", part.get("id").asText(), "quantity", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Estoque insuficiente. Disponível: 2")));

        send("GET", "/api/inventory/parts/" + part.get("id").asText(), admin, null)
                .andExpect(jsonPath("$.stockQuantity", is(2)));
    }

    @Test
    void transicoesDeStatusSaoValidadas() throws Exception {
        String admin = adminToken();
        String id = novaOrdem(admin, null).get("id").asText();

        send("PATCH", "/api/service-orders/" + id + "/status", admin, Map.of("status", "FINALIZADA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Transição de status inválida de CRIADA para FINALIZADA")));

        send("PATCH", "/api/service-orders/" + id + "/status", admin,
                        Map.of("status", "EM_DIAGNOSTICO", "diagnosis", "Pastilhas gastas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis", is("Pastilhas gastas")));
    }

    @Test
    void ordemFinalizadaOuCanceladaNaoAceitaFaturamento() throws Exception {
        String admin = adminToken();
        String id = novaOrdem(admin, null).get("id").asText();
        JsonNode part = novaPeca(admin, 5, "10.00");

        send("PATCH", "/api/service-orders/" + id + "/status", admin, Map.of("status", "CANCELADA"))
                .andExpect(status().isOk());
        send("POST", "/api/service-orders/" + id + "/items", admin,
                        Map.of("partId", part.get("id").asText(), "quantity", 1))
                .andExpect(status().isBadRequest());
        send("PATCH", "/api/service-orders/" + id + "/labor", admin, Map.of("laborAmount", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mecanicoVinculadoAcessaApenasAPropriaOrdem() throws Exception {
        String admin = adminToken();
        User mecUser = createUser(Role.MECHANIC);
        JsonNode mechanic = created("/api/mechanics", admin,
                Map.of("name", "Zé", "userId", mecUser.getId().toString()));
        JsonNode minha = novaOrdem(admin, mechanic.get("id").asText());
        JsonNode outra = novaOrdem(admin, null);
        String token = tokenFor(mecUser);

        send("GET", "/api/service-orders/" + minha.get("id").asText(), token, null).andExpect(status().isOk());
        send("GET", "/api/service-orders/" + outra.get("id").asText(), token, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Esta ordem de serviço não está atribuída a você")));
        send("GET", "/api/service-orders/mine", token, null)
                .andExpect(jsonPath("$.length()", is(1)));
    }

    // ------------------------------------------------------------------ orçamentos e dashboard

    @Test
    void orcamentoSoPodeSerDecididoUmaVez() throws Exception {
        String admin = adminToken();
        JsonNode client = created("/api/clients", admin, Map.of("name", "Cliente Orç"));
        JsonNode vehicle = created("/api/vehicles", admin, Map.of("plate", uniquePlate(), "brand", "Fiat",
                "model", "Palio", "year", 2014, "clientId", client.get("id").asText()));
        JsonNode budget = created("/api/budgets", admin, Map.of("clientId", client.get("id").asText(),
                "vehicleId", vehicle.get("id").asText(), "totalAmount", "480.90"));
        String id = budget.get("id").asText();
        org.junit.jupiter.api.Assertions.assertTrue(budget.get("budgetNumber").asText().matches("BUD\\d{4}\\d{6}"));

        send("PATCH", "/api/budgets/" + id + "/approve", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APROVADO")));
        send("PATCH", "/api/budgets/" + id + "/reject", admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Apenas orçamentos pendentes podem ser recusados")));
    }

    @Test
    void dashboardDevolveSeisMesesDeGrafico() throws Exception {
        String admin = adminToken();
        send("GET", "/api/dashboard/stats", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyRevenueChart.length()", is(6)))
                .andExpect(jsonPath("$.totalClients").isNumber());
        send("GET", "/api/dashboard/stats", tokenFor(createUser(Role.RECEPTIONIST)), null)
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ correções de regras de negócio

    private void mudarStatus(String token, String orderId, String status) throws Exception {
        send("PATCH", "/api/service-orders/" + orderId + "/status", token, Map.of("status", status))
                .andExpect(status().isOk());
    }

    @Test
    void cancelarOsDevolveAsPecasAoEstoque() throws Exception {
        String admin = adminToken();
        String orderId = novaOrdem(admin, null).get("id").asText();
        String partId = novaPeca(admin, 10, "50.00").get("id").asText();

        send("POST", "/api/service-orders/" + orderId + "/items", admin, Map.of("partId", partId, "quantity", 4))
                .andExpect(status().isCreated());
        send("GET", "/api/inventory/parts/" + partId, admin, null).andExpect(jsonPath("$.stockQuantity", is(6)));

        mudarStatus(admin, orderId, "CANCELADA");

        send("GET", "/api/inventory/parts/" + partId, admin, null).andExpect(jsonPath("$.stockQuantity", is(10)));
        // cadastro inicial + saída da OS + estorno do cancelamento
        send("GET", "/api/inventory/parts/" + partId + "/movements", admin, null)
                .andExpect(jsonPath("$.length()", is(3)));
    }

    @Test
    void repetirOMesmoStatusRetorna400() throws Exception {
        String admin = adminToken();
        String id = novaOrdem(admin, null).get("id").asText();

        send("PATCH", "/api/service-orders/" + id + "/status", admin, Map.of("status", "CRIADA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("A ordem de serviço já está com o status Criada")));
    }

    @Test
    void mecanicoNaoAprovaNemCancelaAOs() throws Exception {
        String admin = adminToken();
        User mecUser = createUser(Role.MECHANIC);
        JsonNode mechanic = created("/api/mechanics", admin, Map.of("name", "Zé", "userId", mecUser.getId().toString()));
        String id = novaOrdem(admin, mechanic.get("id").asText()).get("id").asText();
        String token = tokenFor(mecUser);

        // executar o serviço é com o mecânico...
        mudarStatus(token, id, "EM_DIAGNOSTICO");
        mudarStatus(token, id, "AGUARDANDO_APROVACAO");

        // ...mas aprovar ou cancelar não é
        send("PATCH", "/api/service-orders/" + id + "/status", token, Map.of("status", "APROVADA"))
                .andExpect(status().isForbidden());
        send("PATCH", "/api/service-orders/" + id + "/status", token, Map.of("status", "CANCELADA"))
                .andExpect(status().isForbidden());
        send("GET", "/api/service-orders/" + id, admin, null).andExpect(jsonPath("$.status", is("AGUARDANDO_APROVACAO")));
    }

    @Test
    void naoSeAtribuiMecanicoAOsCancelada() throws Exception {
        String admin = adminToken();
        String id = novaOrdem(admin, null).get("id").asText();
        String mechanicId = created("/api/mechanics", admin, Map.of("name", "Zé")).get("id").asText();
        mudarStatus(admin, id, "CANCELADA");

        send("PATCH", "/api/service-orders/" + id + "/mechanic?mechanicId=" + mechanicId, admin, null)
                .andExpect(status().isBadRequest());
    }

    /** Lança mão de obra na OS e cria o orçamento dela (o valor vem do total da OS). */
    private JsonNode orcamentoDaOs(String token, JsonNode order) throws Exception {
        send("PATCH", "/api/service-orders/" + order.get("id").asText() + "/labor", token,
                Map.of("laborAmount", "300.00")).andExpect(status().isOk());
        return created("/api/budgets", token, Map.of(
                "clientId", order.get("clientId").asText(),
                "vehicleId", order.get("vehicleId").asText(),
                "serviceOrderId", order.get("id").asText()));
    }

    @Test
    void aprovarOrcamentoAprovaAOsVinculada() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        String orderId = order.get("id").asText();
        mudarStatus(admin, orderId, "EM_DIAGNOSTICO");
        mudarStatus(admin, orderId, "AGUARDANDO_APROVACAO");
        String budgetId = orcamentoDaOs(admin, order).get("id").asText();

        send("PATCH", "/api/budgets/" + budgetId + "/approve", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APROVADO")));

        send("GET", "/api/service-orders/" + orderId, admin, null).andExpect(jsonPath("$.status", is("APROVADA")));
    }

    @Test
    void recusarOrcamentoCancelaAOsEDevolveAsPecas() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        String orderId = order.get("id").asText();
        String partId = novaPeca(admin, 10, "50.00").get("id").asText();
        send("POST", "/api/service-orders/" + orderId + "/items", admin, Map.of("partId", partId, "quantity", 3))
                .andExpect(status().isCreated());
        mudarStatus(admin, orderId, "EM_DIAGNOSTICO");
        mudarStatus(admin, orderId, "AGUARDANDO_APROVACAO");
        String budgetId = orcamentoDaOs(admin, order).get("id").asText();

        send("PATCH", "/api/budgets/" + budgetId + "/reject", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RECUSADO")));

        send("GET", "/api/service-orders/" + orderId, admin, null).andExpect(jsonPath("$.status", is("CANCELADA")));
        send("GET", "/api/inventory/parts/" + partId, admin, null).andExpect(jsonPath("$.stockQuantity", is(10)));
    }

    @Test
    void orcamentoNaoEhDecididoSeAOsNaoEstiverAguardandoAprovacao() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null); // continua CRIADA
        String budgetId = orcamentoDaOs(admin, order).get("id").asText();

        send("PATCH", "/api/budgets/" + budgetId + "/approve", admin, null).andExpect(status().isBadRequest());

        // a transação foi revertida: o orçamento segue pendente
        send("GET", "/api/budgets/" + budgetId, admin, null).andExpect(jsonPath("$.status", is("PENDENTE")));
    }

    // ------------------------------------------------------------------ paginação

    @Test
    void listagemDeClientesEhPaginadaEFiltravelPorNome() throws Exception {
        String admin = adminToken();
        String marca = "Paginado-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            created("/api/clients", admin, Map.of("name", marca + "-" + i));
        }

        send("GET", "/api/clients?name=" + marca + "&size=2&page=0&sort=name,asc", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(2)))
                .andExpect(jsonPath("$.content[0].name", is(marca + "-0")))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));
        send("GET", "/api/clients?name=" + marca + "&size=2&page=1&sort=name,asc", admin, null)
                .andExpect(jsonPath("$.content.length()", is(1)));
    }

    @Test
    void ordenacaoPorCampoDesconhecidoCaiNoPadraoEmVezDeErro500() throws Exception {
        String admin = adminToken();
        send("GET", "/api/clients?sort=cpf,desc", admin, null).andExpect(status().isOk());
        send("GET", "/api/inventory/parts?sort=campoInexistente", admin, null).andExpect(status().isOk());
        send("GET", "/api/service-orders?sort=campoInexistente", admin, null).andExpect(status().isOk());
    }

    @Test
    void tamanhoDePaginaEhLimitado() throws Exception {
        send("GET", "/api/clients?size=100000", adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(100)));
    }

    @Test
    void listagemDeOrdensPaginaEFiltraPorStatus() throws Exception {
        String admin = adminToken();
        String id = novaOrdem(admin, null).get("id").asText();
        novaOrdem(admin, null);
        mudarStatus(admin, id, "CANCELADA");

        send("GET", "/api/service-orders?status=CANCELADA&size=1", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].status", is("CANCELADA")));
    }

    @Test
    void healthEhPublico() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database", is("up")));
    }

    // ------------------------------------------------------------------ interface web

    @Test
    void paginaDeLoginEhPublicaEAreaRestritaRedireciona() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/clients"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    // ------------------------------------------------------------------ regras de negócio: orçamento

    private JsonNode novoOrcamentoAvulso(String token, String validUntil) throws Exception {
        JsonNode client = created("/api/clients", token, Map.of("name", "Cliente Orç"));
        JsonNode vehicle = created("/api/vehicles", token, Map.of("plate", uniquePlate(), "brand", "Fiat",
                "model", "Palio", "year", 2014, "clientId", client.get("id").asText()));
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("clientId", client.get("id").asText());
        body.put("vehicleId", vehicle.get("id").asText());
        body.put("totalAmount", "100.00");
        if (validUntil != null) {
            body.put("validUntil", validUntil);
        }
        return created("/api/budgets", token, body);
    }

    private void vencer(String budgetId) {
        Budget budget = budgetRepository.findById(UUID.fromString(budgetId)).orElseThrow();
        budget.setValidUntil(Instant.now().minusSeconds(60));
        budgetRepository.saveAndFlush(budget);
    }

    private BudgetStatus statusNoBanco(String budgetId) {
        return budgetRepository.findById(UUID.fromString(budgetId)).orElseThrow().getStatus();
    }

    @Test
    void orcamentoVencidoNaoEhAprovadoEFicaExpiradoNoBanco() throws Exception {
        String admin = adminToken();
        String id = novoOrcamentoAvulso(admin, Instant.now().plusSeconds(3600).toString()).get("id").asText();
        vencer(id);

        send("PATCH", "/api/budgets/" + id + "/approve", admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Orçamento vencido: crie um novo orçamento")));

        // a transação não é revertida: o orçamento foi gravado como expirado
        org.junit.jupiter.api.Assertions.assertEquals(BudgetStatus.EXPIRADO, statusNoBanco(id));
        send("GET", "/api/budgets/" + id, admin, null).andExpect(jsonPath("$.status", is("EXPIRADO")));
    }

    @Test
    void leituraJaMostraPendenteVencidoComoExpirado() throws Exception {
        String admin = adminToken();
        String id = novoOrcamentoAvulso(admin, Instant.now().plusSeconds(3600).toString()).get("id").asText();
        vencer(id);

        send("GET", "/api/budgets/" + id, admin, null).andExpect(jsonPath("$.status", is("EXPIRADO")));
        org.junit.jupiter.api.Assertions.assertEquals(BudgetStatus.PENDENTE, statusNoBanco(id));
    }

    @Test
    void rotinaMarcaOrcamentosVencidosComoExpirados() throws Exception {
        String admin = adminToken();
        String vencido = novoOrcamentoAvulso(admin, Instant.now().plusSeconds(3600).toString()).get("id").asText();
        String valido = novoOrcamentoAvulso(admin, Instant.now().plusSeconds(3600).toString()).get("id").asText();
        vencer(vencido);

        org.junit.jupiter.api.Assertions.assertTrue(budgetService.expireOverdue() >= 1);

        org.junit.jupiter.api.Assertions.assertEquals(BudgetStatus.EXPIRADO, statusNoBanco(vencido));
        org.junit.jupiter.api.Assertions.assertEquals(BudgetStatus.PENDENTE, statusNoBanco(valido));
    }

    @Test
    void validadeNoPassadoEhRecusadaNaCriacao() throws Exception {
        String admin = adminToken();
        JsonNode client = created("/api/clients", admin, Map.of("name", "Cliente Validade"));
        JsonNode vehicle = created("/api/vehicles", admin, Map.of("plate", uniquePlate(), "brand", "Fiat",
                "model", "Palio", "year", 2014, "clientId", client.get("id").asText()));

        send("POST", "/api/budgets", admin, Map.of("clientId", client.get("id").asText(),
                        "vehicleId", vehicle.get("id").asText(), "totalAmount", "100.00",
                        "validUntil", Instant.now().minusSeconds(60).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("A validade do orçamento deve ser uma data futura")));
    }

    @Test
    void orcamentoDeUmaOsUsaOTotalDelaEBarraDivergenciaEDuplicidade() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        String orderId = order.get("id").asText();

        JsonNode budget = orcamentoDaOs(admin, order);
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("300.00").compareTo(new java.math.BigDecimal(budget.get("totalAmount").asText())));

        // segundo orçamento ativo para a mesma OS
        send("POST", "/api/budgets", admin, Map.of("clientId", order.get("clientId").asText(),
                        "vehicleId", order.get("vehicleId").asText(), "serviceOrderId", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Já existe um orçamento ativo para a OS " + order.get("orderNumber").asText())));

        // valor informado diferente do total da OS
        JsonNode outra = novaOrdem(admin, null);
        send("PATCH", "/api/service-orders/" + outra.get("id").asText() + "/labor", admin, Map.of("laborAmount", "300.00"))
                .andExpect(status().isOk());
        send("POST", "/api/budgets", admin, Map.of("clientId", outra.get("clientId").asText(),
                        "vehicleId", outra.get("vehicleId").asText(), "serviceOrderId", outra.get("id").asText(),
                        "totalAmount", "999.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("O valor informado (R$ 999.00) difere do total da OS (R$ 300.00)")));
    }

    @Test
    void orcamentoExpiradoLiberaONovoOrcamentoDaOs() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        JsonNode primeiro = orcamentoDaOs(admin, order);
        budgetRepository.findById(UUID.fromString(primeiro.get("id").asText())).ifPresent(b -> {
            b.setStatus(BudgetStatus.EXPIRADO);
            budgetRepository.saveAndFlush(b);
        });

        created("/api/budgets", admin, Map.of("clientId", order.get("clientId").asText(),
                "vehicleId", order.get("vehicleId").asText(), "serviceOrderId", order.get("id").asText()));
    }

    @Test
    void osAlteradaDepoisDoOrcamentoNaoAprovaAteSincronizar() throws Exception {
        String admin = adminToken();
        JsonNode order = novaOrdem(admin, null);
        String orderId = order.get("id").asText();
        mudarStatus(admin, orderId, "EM_DIAGNOSTICO");
        mudarStatus(admin, orderId, "AGUARDANDO_APROVACAO");
        String budgetId = orcamentoDaOs(admin, order).get("id").asText();

        send("PATCH", "/api/service-orders/" + orderId + "/labor", admin, Map.of("laborAmount", "350.00"))
                .andExpect(status().isOk());

        send("PATCH", "/api/budgets/" + budgetId + "/approve", admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("O total da OS mudou")));

        send("PATCH", "/api/budgets/" + budgetId + "/sync-order-total", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount", amount("350.00")));
        send("PATCH", "/api/budgets/" + budgetId + "/approve", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APROVADO")));
        send("GET", "/api/service-orders/" + orderId, admin, null).andExpect(jsonPath("$.status", is("APROVADA")));
    }

    // ------------------------------------------------------------------ regras de negócio: consistência e inativos

    @Test
    void naoSeAbreOsComVeiculoDeOutroCliente() throws Exception {
        String admin = adminToken();
        JsonNode clienteA = created("/api/clients", admin, Map.of("name", "Cliente A"));
        JsonNode clienteB = created("/api/clients", admin, Map.of("name", "Cliente B"));
        String placa = uniquePlate();
        JsonNode carroDeB = created("/api/vehicles", admin, Map.of("plate", placa, "brand", "Fiat",
                "model", "Uno", "year", 2012, "clientId", clienteB.get("id").asText()));

        send("POST", "/api/service-orders", admin, Map.of("clientId", clienteA.get("id").asText(),
                        "vehicleId", carroDeB.get("id").asText()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("O veículo " + placa + " não pertence ao cliente informado")));
        send("POST", "/api/budgets", admin, Map.of("clientId", clienteA.get("id").asText(),
                        "vehicleId", carroDeB.get("id").asText(), "totalAmount", "100.00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void naoSeAbreOsParaClienteVeiculoOuMecanicoInativos() throws Exception {
        String admin = adminToken();
        JsonNode client = created("/api/clients", admin, Map.of("name", "Cliente Inativo"));
        JsonNode vehicle = created("/api/vehicles", admin, Map.of("plate", uniquePlate(), "brand", "Fiat",
                "model", "Uno", "year", 2012, "clientId", client.get("id").asText()));
        JsonNode mechanic = created("/api/mechanics", admin, Map.of("name", "Mecânico Inativo"));
        Map<String, Object> body = Map.of("clientId", client.get("id").asText(),
                "vehicleId", vehicle.get("id").asText(), "mechanicId", mechanic.get("id").asText());

        // mecânico desativado
        send("DELETE", "/api/mechanics/" + mechanic.get("id").asText(), admin, null).andExpect(status().is2xxSuccessful());
        send("POST", "/api/service-orders", admin, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Mecânico inativo: Mecânico Inativo")));

        // cliente desativado
        Client entity = clientRepository.findById(UUID.fromString(client.get("id").asText())).orElseThrow();
        entity.setActive(false);
        clientRepository.saveAndFlush(entity);
        send("POST", "/api/service-orders", admin, Map.of("clientId", client.get("id").asText(),
                        "vehicleId", vehicle.get("id").asText()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Cliente inativo: Cliente Inativo")));
    }

    // ------------------------------------------------------------------ regras de negócio: mão de obra

    @Test
    void mecanicoLancaPecasMasNaoAlteraAMaoDeObra() throws Exception {
        String admin = adminToken();
        User mecUser = createUser(Role.MECHANIC);
        JsonNode mechanic = created("/api/mechanics", admin, Map.of("name", "Zé", "userId", mecUser.getId().toString()));
        String orderId = novaOrdem(admin, mechanic.get("id").asText()).get("id").asText();
        String partId = novaPeca(admin, 10, "20.00").get("id").asText();
        String token = tokenFor(mecUser);

        send("PATCH", "/api/service-orders/" + orderId + "/labor", token, Map.of("laborAmount", "1.00"))
                .andExpect(status().isForbidden());
        send("POST", "/api/service-orders/" + orderId + "/items", token, Map.of("partId", partId, "quantity", 1))
                .andExpect(status().isCreated());
        send("PATCH", "/api/service-orders/" + orderId + "/labor", tokenFor(createUser(Role.RECEPTIONIST)),
                Map.of("laborAmount", "80.00"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ regras de negócio: pagamentos

    @Test
    void pagamentosParciaisAteQuitarAOs() throws Exception {
        String admin = adminToken();
        String orderId = novaOrdem(admin, null).get("id").asText();
        send("PATCH", "/api/service-orders/" + orderId + "/labor", admin, Map.of("laborAmount", "100.00"))
                .andExpect(status().isOk());

        // OS ainda aberta não recebe pagamento
        send("POST", "/api/service-orders/" + orderId + "/payments", admin, Map.of("amount", "10.00", "method", "PIX"))
                .andExpect(status().isBadRequest());

        for (String status : new String[] {"EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "APROVADA", "EM_EXECUCAO", "FINALIZADA"}) {
            mudarStatus(admin, orderId, status);
        }
        send("GET", "/api/service-orders/" + orderId, admin, null)
                .andExpect(jsonPath("$.paymentStatus", is("PENDENTE")))
                .andExpect(jsonPath("$.balanceAmount", amount("100.00")));

        send("POST", "/api/service-orders/" + orderId + "/payments", admin,
                        Map.of("amount", "40.00", "method", "PIX", "notes", "Sinal"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount", amount("40.00")))
                .andExpect(jsonPath("$.method", is("PIX")));
        send("GET", "/api/service-orders/" + orderId, admin, null)
                .andExpect(jsonPath("$.paymentStatus", is("PARCIAL")))
                .andExpect(jsonPath("$.paidAmount", amount("40.00")))
                .andExpect(jsonPath("$.balanceAmount", amount("60.00")));

        send("POST", "/api/service-orders/" + orderId + "/payments", admin, Map.of("amount", "70.00", "method", "DINHEIRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("O valor excede o saldo em aberto (R$ 60.00)")));

        send("POST", "/api/service-orders/" + orderId + "/payments", admin, Map.of("amount", "60.00", "method", "DINHEIRO"))
                .andExpect(status().isCreated());
        send("GET", "/api/service-orders/" + orderId, admin, null)
                .andExpect(jsonPath("$.paymentStatus", is("PAGA")))
                .andExpect(jsonPath("$.balanceAmount", amount("0")));
        send("POST", "/api/service-orders/" + orderId + "/payments", admin, Map.of("amount", "1.00", "method", "PIX"))
                .andExpect(status().isBadRequest());

        send("GET", "/api/service-orders/" + orderId + "/payments", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    void validacaoDoPagamentoEPermissoes() throws Exception {
        String admin = adminToken();
        String orderId = novaOrdem(admin, null).get("id").asText();

        send("POST", "/api/service-orders/" + orderId + "/payments", admin, Map.of("amount", "0", "method", "PIX"))
                .andExpect(status().isUnprocessableEntity());
        send("POST", "/api/service-orders/" + orderId + "/payments", admin, Map.of("amount", "10.00"))
                .andExpect(status().isUnprocessableEntity());
        send("POST", "/api/service-orders/" + orderId + "/payments", tokenFor(createUser(Role.MECHANIC)),
                        Map.of("amount", "10.00", "method", "PIX"))
                .andExpect(status().isForbidden());
        send("GET", "/api/service-orders/" + orderId + "/payments", tokenFor(createUser(Role.MECHANIC)), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void osFinalizadaSemValorJaFicaQuitada() throws Exception {
        String admin = adminToken();
        String orderId = novaOrdem(admin, null).get("id").asText();
        for (String status : new String[] {"EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "APROVADA", "EM_EXECUCAO", "FINALIZADA"}) {
            mudarStatus(admin, orderId, status);
        }

        send("GET", "/api/service-orders/" + orderId, admin, null).andExpect(jsonPath("$.paymentStatus", is("PAGA")));
    }

    // ------------------------------------------------------------------ regras de negócio: histórico de status

    @Test
    void historicoRegistraAberturaEMudancasComOAutor() throws Exception {
        String admin = adminToken();
        String orderId = novaOrdem(admin, null).get("id").asText();
        mudarStatus(admin, orderId, "EM_DIAGNOSTICO");
        mudarStatus(admin, orderId, "CANCELADA");

        send("GET", "/api/service-orders/" + orderId + "/history", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)))
                .andExpect(jsonPath("$[0].fromStatus", is(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[0].toStatus", is("CRIADA")))
                .andExpect(jsonPath("$[1].fromStatus", is("CRIADA")))
                .andExpect(jsonPath("$[1].toStatus", is("EM_DIAGNOSTICO")))
                .andExpect(jsonPath("$[2].toStatus", is("CANCELADA")))
                .andExpect(jsonPath("$[2].changedByName", is("Usuário ADMIN")))
                .andExpect(jsonPath("$[2].changedAt").isNotEmpty());
    }

    @Test
    void mecanicoSoVeOHistoricoDaPropriaOs() throws Exception {
        String admin = adminToken();
        User mecUser = createUser(Role.MECHANIC);
        JsonNode mechanic = created("/api/mechanics", admin, Map.of("name", "Zé", "userId", mecUser.getId().toString()));
        String minha = novaOrdem(admin, mechanic.get("id").asText()).get("id").asText();
        String outra = novaOrdem(admin, null).get("id").asText();
        String token = tokenFor(mecUser);

        send("GET", "/api/service-orders/" + minha + "/history", token, null).andExpect(status().isOk());
        send("GET", "/api/service-orders/" + outra + "/history", token, null).andExpect(status().isForbidden());
    }
}
