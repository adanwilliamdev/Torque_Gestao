package com.torquegestao.web;

import com.torquegestao.domain.PaymentMethod;
import com.torquegestao.domain.Role;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.dto.ServiceOrderResponse;
import com.torquegestao.dto.StatusUpdateRequest;
import com.torquegestao.exception.AppException;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.security.Access;
import com.torquegestao.security.AppUserDetails;
import com.torquegestao.service.ClientService;
import com.torquegestao.service.InventoryService;
import com.torquegestao.service.MechanicService;
import com.torquegestao.service.PaymentService;
import com.torquegestao.service.ServiceOrderService;
import com.torquegestao.service.VehicleService;
import com.torquegestao.web.form.ItemForm;
import com.torquegestao.web.form.LaborForm;
import com.torquegestao.web.form.PaymentForm;
import com.torquegestao.web.form.ServiceOrderForm;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/service-orders")
public class ServiceOrderWebController {

    private final ServiceOrderService orders;
    private final ClientService clients;
    private final VehicleService vehicles;
    private final MechanicService mechanics;
    private final InventoryService inventory;
    private final PaymentService payments;

    public ServiceOrderWebController(
            ServiceOrderService orders,
            ClientService clients,
            VehicleService vehicles,
            MechanicService mechanics,
            InventoryService inventory,
            PaymentService payments) {
        this.orders = orders;
        this.clients = clients;
        this.vehicles = vehicles;
        this.mechanics = mechanics;
        this.inventory = inventory;
        this.payments = payments;
    }

    @GetMapping
    @PreAuthorize(Access.ORDER_VIEW)
    public String list(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("orders", orders.listVisibleTo(user));
        return "service-orders/list";
    }

    @GetMapping("/new")
    @PreAuthorize(Access.ORDER_CREATE)
    public String create(Model model) {
        model.addAttribute("form", new ServiceOrderForm());
        return formView(model);
    }

    @PostMapping
    @PreAuthorize(Access.ORDER_CREATE)
    public String save(
            @Valid @ModelAttribute("form") ServiceOrderForm form,
            BindingResult errors,
            @AuthenticationPrincipal AppUserDetails user,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return formView(model);
        }
        try {
            orders.create(form.toRequest(), user.getId());
        } catch (AppException | DataIntegrityViolationException ex) {
            errors.reject("business", ClientWebController.messageOf(ex));
            return formView(model);
        }
        flash.addFlashAttribute("success", "Ordem de serviço criada com sucesso");
        return "redirect:/service-orders";
    }

    /** Avança a OS para o próximo passo do fluxo. */
    @PostMapping("/{id}/advance")
    @PreAuthorize(Access.ORDER_STATUS)
    public String advance(
            @PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user, RedirectAttributes flash) {
        ServiceOrderResponse order = orders.get(id, user);
        ServiceOrderStatus next = order.status().nextStep();
        if (next == null) {
            throw new BusinessException("Esta ordem de serviço já foi encerrada");
        }
        orders.updateStatus(id, new StatusUpdateRequest(next, null), user);
        flash.addFlashAttribute("success", "Status atualizado para " + next.getLabel());
        return "redirect:/service-orders";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(Access.ORDER_STATUS)
    public String cancel(
            @PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user, RedirectAttributes flash) {
        orders.updateStatus(id, new StatusUpdateRequest(ServiceOrderStatus.CANCELADA, null), user);
        flash.addFlashAttribute("success", "Ordem de serviço cancelada");
        return "redirect:/service-orders";
    }

    // ------------------------------------------------------------------ faturamento

    @GetMapping("/{id}/billing")
    @PreAuthorize(Access.ORDER_BILLING)
    public String billing(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user, Model model) {
        return billingView(model, orders.get(id, user), user);
    }

    @PostMapping("/{id}/items")
    @PreAuthorize(Access.ORDER_BILLING)
    public String addItem(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails user,
            @Valid @ModelAttribute("itemForm") ItemForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        ServiceOrderResponse order = orders.get(id, user);
        if (!errors.hasErrors()) {
            try {
                orders.addItem(id, form.toRequest(), user);
                flash.addFlashAttribute("success", "Peça adicionada à ordem de serviço");
                return "redirect:/service-orders/" + id + "/billing";
            } catch (AppException ex) {
                errors.reject("business", ex.getMessage());
            }
        }
        return billingView(model, order, user);
    }

    @PostMapping("/{id}/items/{itemId}/remove")
    @PreAuthorize(Access.ORDER_BILLING)
    public String removeItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal AppUserDetails user,
            RedirectAttributes flash) {
        orders.removeItem(id, itemId, user);
        flash.addFlashAttribute("success", "Peça removida e estoque estornado");
        return "redirect:/service-orders/" + id + "/billing";
    }

    @PostMapping("/{id}/labor")
    @PreAuthorize(Access.ORDER_LABOR)
    public String labor(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails user,
            @Valid @ModelAttribute("laborForm") LaborForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        ServiceOrderResponse order = orders.get(id, user);
        if (!errors.hasErrors()) {
            try {
                orders.updateLaborAmount(id, form.getLaborAmount(), user);
                flash.addFlashAttribute("success", "Mão de obra atualizada");
                return "redirect:/service-orders/" + id + "/billing";
            } catch (AppException ex) {
                errors.reject("business", ex.getMessage());
            }
        }
        return billingView(model, order, user);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize(Access.PAYMENT_WRITE)
    public String addPayment(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails user,
            @Valid @ModelAttribute("paymentForm") PaymentForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        ServiceOrderResponse order = orders.get(id, user);
        if (!errors.hasErrors()) {
            try {
                payments.register(id, form.toRequest(), user.getId());
                flash.addFlashAttribute("success", "Pagamento registrado");
                return "redirect:/service-orders/" + id + "/billing";
            } catch (AppException ex) {
                errors.reject("business", ex.getMessage());
            }
        }
        return billingView(model, order, user);
    }

    private String formView(Model model) {
        model.addAttribute("clients", clients.list());
        model.addAttribute("vehicles", vehicles.list());
        model.addAttribute("mechanics", mechanics.listAvailable());
        return "service-orders/form";
    }

    /** Monta a página de faturamento; formulários já presentes no model (com erros) são preservados. */
    private String billingView(Model model, ServiceOrderResponse order, AppUserDetails user) {
        if (!model.containsAttribute("itemForm")) {
            model.addAttribute("itemForm", new ItemForm());
        }
        if (!model.containsAttribute("laborForm")) {
            LaborForm labor = new LaborForm();
            labor.setLaborAmount(order.laborAmount());
            model.addAttribute("laborForm", labor);
        }
        if (!model.containsAttribute("paymentForm")) {
            PaymentForm payment = new PaymentForm();
            if (order.balanceAmount().signum() > 0) {
                payment.setAmount(order.balanceAmount());
            }
            model.addAttribute("paymentForm", payment);
        }
        model.addAttribute("order", order);
        model.addAttribute("parts", inventory.list());
        model.addAttribute("history", orders.history(order.id(), user));
        // Financeiro é da recepção/gerência; o mecânico não vê nem registra pagamentos.
        boolean financeVisible = user.getRole() != Role.MECHANIC;
        model.addAttribute("financeVisible", financeVisible);
        model.addAttribute("payments", financeVisible ? payments.list(order.id()) : java.util.List.of());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "service-orders/billing";
    }
}
