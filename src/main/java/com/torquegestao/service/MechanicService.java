package com.torquegestao.service;

import com.torquegestao.domain.Mechanic;
import com.torquegestao.dto.MechanicRequest;
import com.torquegestao.dto.MechanicResponse;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.MechanicRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MechanicService {

    private final MechanicRepository mechanics;

    public MechanicService(MechanicRepository mechanics) {
        this.mechanics = mechanics;
    }

    public Mechanic getMechanic(UUID id) {
        return mechanics.findById(id)
                .orElseThrow(() -> new NotFoundException("Mecânico não encontrado com ID: " + id));
    }

    /** Mecânico que ainda pode receber ordens de serviço (a exclusão é lógica). */
    public Mechanic getActiveMechanic(UUID id) {
        Mechanic mechanic = getMechanic(id);
        if (!mechanic.isActive()) {
            throw new BusinessException("Mecânico inativo: " + mechanic.getName());
        }
        return mechanic;
    }

    @Transactional(readOnly = true)
    public Optional<Mechanic> findByUserId(UUID userId) {
        return mechanics.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public MechanicResponse getMechanicResponse(UUID id) {
        return MechanicResponse.from(getMechanic(id));
    }

    @Transactional(readOnly = true)
    public List<MechanicResponse> list() {
        return mechanics.findByActiveTrueOrderByCreatedAtDesc().stream().map(MechanicResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MechanicResponse> listAvailable() {
        return mechanics.findByAvailableTrueAndActiveTrue().stream().map(MechanicResponse::from).toList();
    }

    public MechanicResponse create(MechanicRequest data) {
        if (data.userId() != null) {
            assertUserNotLinked(data.userId());
        }
        Mechanic mechanic = new Mechanic();
        mechanic.setUserId(data.userId());
        mechanic.setName(data.name().trim());
        mechanic.setSpecialty(Texts.blankToNull(data.specialty()));
        mechanic.setPhone(Texts.blankToNull(data.phone()));
        mechanic.setAvailable(true);
        mechanic.setActive(true);
        mechanics.saveAndFlush(mechanic);
        return MechanicResponse.from(mechanic);
    }

    /** {@code userId} nulo na requisição mantém o vínculo atual. */
    public MechanicResponse update(UUID id, MechanicRequest data) {
        Mechanic mechanic = getMechanic(id);
        if (data.userId() != null && !data.userId().equals(mechanic.getUserId())) {
            assertUserNotLinked(data.userId());
            mechanic.setUserId(data.userId());
        }
        mechanic.setName(data.name().trim());
        mechanic.setSpecialty(Texts.blankToNull(data.specialty()));
        mechanic.setPhone(Texts.blankToNull(data.phone()));
        mechanics.saveAndFlush(mechanic);
        return MechanicResponse.from(mechanic);
    }

    public void deactivate(UUID id) {
        getMechanic(id).setActive(false);
    }

    public void setAvailability(UUID id, boolean available) {
        getMechanic(id).setAvailable(available);
    }

    private void assertUserNotLinked(UUID userId) {
        if (mechanics.findByUserId(userId).isPresent()) {
            throw new BusinessException("Este usuário já está vinculado a outro perfil de mecânico");
        }
    }
}
