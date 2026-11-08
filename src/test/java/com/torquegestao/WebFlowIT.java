package com.torquegestao;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.torquegestao.domain.Client;
import com.torquegestao.domain.Mechanic;
import com.torquegestao.domain.Role;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.domain.User;
import com.torquegestao.domain.Vehicle;
import com.torquegestao.dto.ServiceOrderRequest;
import com.torquegestao.dto.ServiceOrderResponse;
import com.torquegestao.dto.StatusUpdateRequest;
import com.torquegestao.repository.ClientRepository;
import com.torquegestao.repository.MechanicRepository;
import com.torquegestao.repository.UserRepository;
import com.torquegestao.repository.VehicleRepository;
import com.torquegestao.security.AppUserDetails;
import com.torquegestao.service.ServiceOrderService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Renderiza as páginas Thymeleaf de ponta a ponta para pegar erros de template. */
class WebFlowIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ClientRepository clients;
    @Autowired VehicleRepository vehicles;
    @Autowired MechanicRepository mechanics;
    @Autowired ServiceOrderService orderService;

    private AppUserDetails principal(Role role) {
        User u = new User();
        u.setName("Teste " + role);
        u.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@teste.com");
        u.setPasswordHash(encoder.encode("x12345"));
        u.setRole(role);
        u.setActive(true);
        return new AppUserDetails(users.saveAndFlush(u));
    }

    @Test
    void todasAsListagensRenderizamParaOAdmin() throws Exception {
        var admin = user(principal(Role.ADMIN));
        for (String path : new String[] {"/", "/clients", "/vehicles", "/mechanics", "/service-orders",
                "/budgets", "/inventory", "/clients/new", "/vehicles/new", "/mechanics/new",
                "/service-orders/new", "/budgets/new", "/inventory/new"}) {
            mvc.perform(get(path).with(admin))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test
    void recepcionistaEhRedirecionadoDaHomeParaClientes() throws Exception {
        mvc.perform(get("/").with(user(principal(Role.RECEPTIONIST))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clients"));
    }

    @Test
    void mecanicoNaoAcessaEstoque() throws Exception {
        mvc.perform(get("/inventory").with(user(principal(Role.MECHANIC))))
                .andExpect(status().isForbidden());
    }

    @Test
    void criarClientePeloFormularioRedirecionaParaALista() throws Exception {
        var admin = user(principal(Role.ADMIN));
        mvc.perform(post("/clients").with(admin).with(csrf())
                        .param("name", "Cliente Web").param("email", "web@teste.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clients"));
    }

    @Test
    void formularioInvalidoVoltaComErrosSemRedirecionar() throws Exception {
        var admin = user(principal(Role.ADMIN));
        mvc.perform(post("/clients").with(admin).with(csrf()).param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("clients/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nome é obrigatório")));
    }

    @Test
    void postSemCsrfEhRecusado() throws Exception {
        mvc.perform(post("/clients").with(user(principal(Role.ADMIN))).param("name", "X"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ faturamento, pagamentos e histórico

    private ServiceOrderResponse novaOs(AppUserDetails abertaPor, UUID mechanicId) {
        Client client = new Client();
        client.setName("Cliente Faturamento");
        client = clients.saveAndFlush(client);
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("W" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        vehicle.setBrand("Fiat");
        vehicle.setModel("Uno");
        vehicle.setYear(2012);
        vehicle.setClient(client);
        vehicle = vehicles.saveAndFlush(vehicle);
        return orderService.create(
                new ServiceOrderRequest(client.getId(), vehicle.getId(), mechanicId, "Barulho"), abertaPor.getId());
    }

    private void finalizar(AppUserDetails admin, UUID orderId) {
        for (ServiceOrderStatus next : new ServiceOrderStatus[] {
                ServiceOrderStatus.EM_DIAGNOSTICO, ServiceOrderStatus.AGUARDANDO_APROVACAO,
                ServiceOrderStatus.APROVADA, ServiceOrderStatus.EM_EXECUCAO, ServiceOrderStatus.FINALIZADA}) {
            orderService.updateStatus(orderId, new StatusUpdateRequest(next, null), admin);
        }
    }

    @Test
    void paginaDeFaturamentoMostraHistoricoEPermiteRegistrarPagamento() throws Exception {
        AppUserDetails admin = principal(Role.ADMIN);
        ServiceOrderResponse order = novaOs(admin, null);
        orderService.updateLaborAmount(order.id(), new BigDecimal("100.00"), admin);
        finalizar(admin, order.id());

        mvc.perform(get("/service-orders/" + order.id() + "/billing").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Histórico de status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar pagamento")))
                // OS finalizada está encerrada: o formulário de mão de obra não deve ser exibido
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/labor"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Esta OS está encerrada")));

        mvc.perform(post("/service-orders/" + order.id() + "/payments").with(user(admin)).with(csrf())
                        .param("amount", "40.00").param("method", "PIX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/service-orders/" + order.id() + "/billing"));

        mvc.perform(get("/service-orders/" + order.id() + "/billing").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pago parcialmente")));
    }

    @Test
    void osAbertaMostraFormularioDeMaoDeObra() throws Exception {
        AppUserDetails admin = principal(Role.ADMIN);
        ServiceOrderResponse order = novaOs(admin, null);

        mvc.perform(get("/service-orders/" + order.id() + "/billing").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/labor")));
    }

    @Test
    void pagamentoAcimaDoSaldoVoltaComMensagemDeErro() throws Exception {
        AppUserDetails admin = principal(Role.ADMIN);
        ServiceOrderResponse order = novaOs(admin, null);
        orderService.updateLaborAmount(order.id(), new BigDecimal("100.00"), admin);
        finalizar(admin, order.id());

        mvc.perform(post("/service-orders/" + order.id() + "/payments").with(user(admin)).with(csrf())
                        .param("amount", "150.00").param("method", "PIX"))
                .andExpect(status().isOk())
                .andExpect(view().name("service-orders/billing"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("O valor excede o saldo em aberto")));
    }

    @Test
    void mecanicoVeFaturamentoSemMaoDeObraNemPagamentos() throws Exception {
        AppUserDetails admin = principal(Role.ADMIN);
        AppUserDetails mecanico = principal(Role.MECHANIC);
        Mechanic mechanic = new Mechanic();
        mechanic.setName("Zé");
        mechanic.setUserId(mecanico.getId());
        mechanic = mechanics.saveAndFlush(mechanic);
        ServiceOrderResponse order = novaOs(admin, mechanic.getId());

        mvc.perform(get("/service-orders/" + order.id() + "/billing").with(user(mecanico)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Histórico de status")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/labor"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Registrar pagamento"))));

        mvc.perform(post("/service-orders/" + order.id() + "/labor").with(user(mecanico)).with(csrf())
                        .param("laborAmount", "1.00"))
                .andExpect(status().isForbidden());
    }
}
