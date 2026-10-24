package com.torquegestao.api;

import com.torquegestao.dto.ClientRequest;
import com.torquegestao.dto.ClientResponse;
import com.torquegestao.dto.PageResponse;
import com.torquegestao.security.Access;
import com.torquegestao.service.ClientService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Clientes")
@RestController
@RequestMapping("/api/clients")
public class ClientApiController {

    private final ClientService clients;

    public ClientApiController(ClientService clients) {
        this.clients = clients;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.CLIENT_WRITE)
    public ClientResponse create(@Valid @RequestBody ClientRequest data) {
        return clients.create(data);
    }

    @GetMapping
    @PreAuthorize(Access.CLIENT_READ)
    public PageResponse<ClientResponse> list(
            @RequestParam(required = false) String name, @PageableDefault(size = 20) Pageable pageable) {
        return clients.page(name, pageable);
    }

    @GetMapping("/search")
    @PreAuthorize(Access.CLIENT_READ)
    public List<ClientResponse> search(@RequestParam(defaultValue = "") String name) {
        return clients.search(name);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Access.CLIENT_READ)
    public ClientResponse get(@PathVariable UUID id) {
        return clients.getClientResponse(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Access.CLIENT_WRITE)
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest data) {
        return clients.update(id, data);
    }

    /** Exclusão lógica: marca o cliente como inativo. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Access.CLIENT_WRITE)
    public void delete(@PathVariable UUID id) {
        clients.setActive(id, false);
    }

    @PatchMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Access.CLIENT_WRITE)
    public void activate(@PathVariable UUID id) {
        clients.setActive(id, true);
    }
}
