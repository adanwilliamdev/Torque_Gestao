package com.torquegestao.service;

import com.torquegestao.domain.Client;
import com.torquegestao.dto.ClientRequest;
import com.torquegestao.dto.ClientResponse;
import com.torquegestao.dto.PageResponse;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.ClientRepository;
import com.torquegestao.repository.VehicleRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clients;
    private final VehicleRepository vehicles;

    public ClientService(ClientRepository clients, VehicleRepository vehicles) {
        this.clients = clients;
        this.vehicles = vehicles;
    }

    public Client getClient(UUID id) {
        return clients.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente não encontrado com ID: " + id));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClientResponse(UUID id) {
        Client client = getClient(id);
        return ClientResponse.from(client, (int) vehicles.countByClientId(id));
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        Map<UUID, Integer> counts = vehicles.vehicleCountsByClient();
        return clients.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(c -> ClientResponse.from(c, counts.getOrDefault(c.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> page(String name, Pageable pageable) {
        Pageable safe = Pages.safe(pageable, Set.of("name", "createdAt"), Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<UUID, Integer> counts = vehicles.vehicleCountsByClient();
        Page<Client> result = clients.findByActiveTrueAndNameContainingIgnoreCase(
                name == null ? "" : name.trim(), safe);
        return PageResponse.from(result.map(c -> ClientResponse.from(c, counts.getOrDefault(c.getId(), 0))));
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> search(String name) {
        Map<UUID, Integer> counts = vehicles.vehicleCountsByClient();
        return clients.searchByName(name).stream()
                .map(c -> ClientResponse.from(c, counts.getOrDefault(c.getId(), 0)))
                .toList();
    }

    public ClientResponse create(ClientRequest data) {
        Client client = new Client();
        apply(client, data);
        client.setActive(true);
        clients.saveAndFlush(client);
        return ClientResponse.from(client, 0);
    }

    public ClientResponse update(UUID id, ClientRequest data) {
        Client client = getClient(id);
        apply(client, data);
        clients.saveAndFlush(client);
        return ClientResponse.from(client, (int) vehicles.countByClientId(id));
    }

    public void setActive(UUID id, boolean active) {
        getClient(id).setActive(active);
    }

    private static void apply(Client client, ClientRequest data) {
        client.setName(data.name().trim());
        client.setCpf(Texts.blankToNull(data.cpf()));
        client.setPhone(Texts.blankToNull(data.phone()));
        client.setEmail(Texts.blankToNull(data.email()));
        client.setAddress(Texts.blankToNull(data.address()));
    }
}
