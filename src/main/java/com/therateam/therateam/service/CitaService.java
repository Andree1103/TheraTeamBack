package com.therateam.therateam.service;

import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.Sesion;
import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.specification.CitaSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CitaService {

    private final CitaRepository repository;

    public List<CitaDTO> findAll() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    public List<CitaDTO> findByFiltros(LocalDateTime fechaInicio, LocalDateTime fechaFin, String terapeuta) {
        return repository.findAll(CitaSpecification.byFiltros(fechaInicio, fechaFin, terapeuta))
                .stream().map(this::toDTO).toList();
    }

    public Optional<CitaDTO> findById(Long id) {
        return repository.findById(id).map(this::toDTO);
    }

    public Cita save(Cita cita) {
        return repository.save(cita);
    }

    public Optional<Cita> update(Long id, Cita data) {
        return repository.findById(id).map(existing -> {
            existing.setSesion(data.getSesion());
            existing.setPaciente(data.getPaciente());
            existing.setEstado(data.getEstado());
            existing.setMotivoCancelacion(data.getMotivoCancelacion());
            existing.setNotasPrevias(data.getNotasPrevias());
            existing.setNotasPost(data.getNotasPost());
            existing.setLinkVideollamada(data.getLinkVideollamada());
            existing.setRecordatorioEnviado(data.getRecordatorioEnviado());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    public CitaDTO toDTO(Cita cita) {
        CitaDTO dto = new CitaDTO();
        dto.setId(cita.getId());
        dto.setEstado(cita.getEstado());
        dto.setMotivoCancelacion(cita.getMotivoCancelacion());
        dto.setNotasPrevias(cita.getNotasPrevias());
        dto.setNotasPost(cita.getNotasPost());
        dto.setLinkVideollamada(cita.getLinkVideollamada());
        dto.setRecordatorioEnviado(cita.getRecordatorioEnviado());

        Paciente p = cita.getPaciente();
        if (p != null) {
            dto.setPacienteId(p.getId());
            dto.setPacienteNombre(p.getNombre());
            dto.setPacienteApellido(p.getApellido());
            dto.setPacienteDni(p.getDni());
            dto.setPacienteTelefono(p.getTelefono());
            dto.setPacienteCorreo(p.getCorreo());
        }

        Sesion s = cita.getSesion();
        if (s != null) {
            dto.setSesionId(s.getId());
            dto.setFechaInicio(s.getFechaInicio());
            dto.setFechaFin(s.getFechaFin());
            dto.setDuracionMinutos(s.getDuracionMinutos());
            dto.setObservacion(s.getObservacion());

            if (s.getTerapeuta() != null) {
                Usuario u = s.getTerapeuta().getUsuario();
                if (u != null) {
                    dto.setTerapeutaNombre(u.getNombre() + " " + u.getApellido());
                }
            }

            if (s.getTipoTerapia() != null) {
                dto.setTipoTerapiaKey(s.getTipoTerapia().getKey());
                dto.setTipoTerapiaNombre(s.getTipoTerapia().getNombre());
            }
        }

        return dto;
    }
}
