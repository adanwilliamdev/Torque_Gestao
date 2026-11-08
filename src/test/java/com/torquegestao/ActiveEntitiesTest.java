package com.torquegestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.torquegestao.domain.Client;
import com.torquegestao.domain.Mechanic;
import com.torquegestao.domain.Vehicle;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.repository.ClientRepository;
import com.torquegestao.repository.MechanicRepository;
import com.torquegestao.repository.VehicleRepository;
import com.torquegestao.service.ClientService;
import com.torquegestao.service.MechanicService;
import com.torquegestao.service.VehicleService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Cliente, veículo e mecânico inativos (exclusão lógica) não entram em novos atendimentos. */
@ExtendWith(MockitoExtension.class)
class ActiveEntitiesTest {

    @Mock ClientRepository clients;
    @Mock VehicleRepository vehicles;
    @Mock MechanicRepository mechanics;
    @Mock ClientService clientServiceMock;

    private Client client(boolean active) {
        Client c = new Client();
        c.setId(UUID.randomUUID());
        c.setName("Fulano");
        c.setActive(active);
        return c;
    }

    private Vehicle vehicle(Client owner, boolean active) {
        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID());
        v.setPlate("ABC1D23");
        v.setClient(owner);
        v.setActive(active);
        return v;
    }

    @Test
    void clienteInativoEhRecusado() {
        Client c = client(false);
        when(clients.findById(c.getId())).thenReturn(Optional.of(c));
        ClientService service = new ClientService(clients, vehicles);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getActiveClient(c.getId()));

        assertEquals("Cliente inativo: Fulano", ex.getMessage());
    }

    @Test
    void clienteAtivoEhDevolvido() {
        Client c = client(true);
        when(clients.findById(c.getId())).thenReturn(Optional.of(c));

        assertSame(c, new ClientService(clients, vehicles).getActiveClient(c.getId()));
    }

    @Test
    void mecanicoInativoEhRecusado() {
        Mechanic m = new Mechanic();
        m.setId(UUID.randomUUID());
        m.setName("Zé");
        m.setActive(false);
        when(mechanics.findById(m.getId())).thenReturn(Optional.of(m));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> new MechanicService(mechanics).getActiveMechanic(m.getId()));

        assertEquals("Mecânico inativo: Zé", ex.getMessage());
    }

    @Test
    void veiculoInativoEhRecusado() {
        Client owner = client(true);
        Vehicle v = vehicle(owner, false);
        when(vehicles.findWithClientById(v.getId())).thenReturn(Optional.of(v));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> new VehicleService(vehicles, clientServiceMock).getActiveVehicleOf(v.getId(), owner.getId()));

        assertEquals("Veículo inativo: ABC1D23", ex.getMessage());
    }

    @Test
    void veiculoDeOutroClienteEhRecusado() {
        Client owner = client(true);
        Vehicle v = vehicle(owner, true);
        when(vehicles.findWithClientById(v.getId())).thenReturn(Optional.of(v));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> new VehicleService(vehicles, clientServiceMock).getActiveVehicleOf(v.getId(), UUID.randomUUID()));

        assertEquals("O veículo ABC1D23 não pertence ao cliente informado", ex.getMessage());
    }

    @Test
    void veiculoAtivoDoClienteEhDevolvido() {
        Client owner = client(true);
        Vehicle v = vehicle(owner, true);
        when(vehicles.findWithClientById(v.getId())).thenReturn(Optional.of(v));

        assertSame(v, new VehicleService(vehicles, clientServiceMock).getActiveVehicleOf(v.getId(), owner.getId()));
    }
}
