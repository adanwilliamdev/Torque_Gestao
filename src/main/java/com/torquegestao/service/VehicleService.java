package com.torquegestao.service;

import com.torquegestao.domain.Vehicle;
import com.torquegestao.dto.VehicleRequest;
import com.torquegestao.dto.VehicleResponse;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VehicleService {

    private final VehicleRepository vehicles;
    private final ClientService clientService;

    public VehicleService(VehicleRepository vehicles, ClientService clientService) {
        this.vehicles = vehicles;
        this.clientService = clientService;
    }

    public Vehicle getVehicle(UUID id) {
        return vehicles.findWithClientById(id)
                .orElseThrow(() -> new NotFoundException("Veículo não encontrado com ID: " + id));
    }

    /**
     * Veículo ativo e pertencente ao cliente informado: impede abrir OS/orçamento com o carro de um
     * cliente no nome de outro, ou com um veículo desativado.
     */
    public Vehicle getActiveVehicleOf(UUID vehicleId, UUID clientId) {
        Vehicle vehicle = getVehicle(vehicleId);
        if (!vehicle.isActive()) {
            throw new BusinessException("Veículo inativo: " + vehicle.getPlate());
        }
        if (!vehicle.getClient().getId().equals(clientId)) {
            throw new BusinessException("O veículo " + vehicle.getPlate() + " não pertence ao cliente informado");
        }
        return vehicle;
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleResponse(UUID id) {
        return VehicleResponse.from(getVehicle(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list() {
        return vehicles.findByActiveTrueOrderByCreatedAtDesc().stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listByClient(UUID clientId) {
        clientService.getClient(clientId);
        return vehicles.findByClientIdAndActiveTrueOrderByCreatedAtDesc(clientId).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    public VehicleResponse create(VehicleRequest data) {
        assertPlateFree(data.plate());
        Vehicle vehicle = new Vehicle();
        apply(vehicle, data);
        vehicle.setActive(true);
        vehicles.saveAndFlush(vehicle);
        return VehicleResponse.from(vehicle);
    }

    public VehicleResponse update(UUID id, VehicleRequest data) {
        Vehicle vehicle = getVehicle(id);
        if (!vehicle.getPlate().equals(data.plate())) {
            assertPlateFree(data.plate());
        }
        apply(vehicle, data);
        vehicles.saveAndFlush(vehicle);
        return VehicleResponse.from(vehicle);
    }

    public void deactivate(UUID id) {
        getVehicle(id).setActive(false);
    }

    public void updateMileage(UUID id, int mileage) {
        getVehicle(id).setMileage(mileage);
    }

    private void apply(Vehicle vehicle, VehicleRequest data) {
        // Valida a existência do cliente (404) antes de vincular.
        vehicle.setClient(clientService.getClient(data.clientId()));
        vehicle.setPlate(data.plate());
        vehicle.setBrand(data.brand().trim());
        vehicle.setModel(data.model().trim());
        vehicle.setYear(data.year());
        vehicle.setMileage(data.mileage());
        vehicle.setFuelType(Texts.blankToNull(data.fuelType()));
    }

    private void assertPlateFree(String plate) {
        if (vehicles.existsByPlate(plate)) {
            throw new BusinessException("Já existe um veículo com esta placa: " + plate);
        }
    }
}
