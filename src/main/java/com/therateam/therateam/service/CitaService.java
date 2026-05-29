package com.therateam.therateam.service;

import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.model.*;
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

    public Cita save(Cita cita) { return repository.save(cita); }

    public Optional<Cita> update(Long id, Cita data) {
        return repository.findById(id).map(e -> {
            e.setTerapeuta(data.getTerapeuta());
            e.setModalidad(data.getModalidad());
            e.setFechaInicio(data.getFechaInicio());
            e.setFechaFin(data.getFechaFin());
            e.setDuracionMinutos(data.getDuracionMinutos());
            e.setEstado(data.getEstado());
            e.setLinkVideollamada(data.getLinkVideollamada());
            e.setNotasPrevias(data.getNotasPrevias());
            e.setRecordatorioEnviado(data.getRecordatorioEnviado());
            return repository.save(e);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    public CitaDTO toDTO(Cita c) {
        CitaDTO dto = new CitaDTO();
        dto.setId(c.getId());
        dto.setFechaInicio(c.getFechaInicio());
        dto.setFechaFin(c.getFechaFin());
        dto.setDuracionMinutos(c.getDuracionMinutos());
        dto.setLinkVideollamada(c.getLinkVideollamada());
        dto.setNotasPrevias(c.getNotasPrevias());
        dto.setRecordatorioEnviado(c.getRecordatorioEnviado());

        // Estado
        if (c.getEstado() != null) {
            dto.setEstado(c.getEstado().getKey());
            dto.setEstadoNombre(c.getEstado().getNombre());
            dto.setEstadoColor(c.getEstado().getColorHex());
        }

        // Modalidad
        if (c.getModalidad() != null) {
            dto.setModalidad(c.getModalidad().getKey());
        }

        // Terapeuta (directo en cita)
        if (c.getTerapeuta() != null && c.getTerapeuta().getUsuario() != null) {
            Usuario u = c.getTerapeuta().getUsuario();
            dto.setTerapeutaNombre(u.getNombre() + " " + u.getApellido());
            dto.setTerapeutaId(c.getTerapeuta().getId());
        }

        // Sesion → Tratamiento → Paciente y TipoTerapia
        Sesion s = c.getSesion();
        if (s != null) {
            dto.setSesionId(s.getId());
            dto.setNumeroSesion(s.getNumero());

            Tratamiento t = s.getTratamiento();
            if (t != null) {
                dto.setTotalSesiones(t.getTotalSesiones());
                dto.setObservacion(t.getNotas());

                Paciente p = t.getPaciente();
                if (p != null) {
                    dto.setPacienteId(p.getId());
                    dto.setPacienteNombre(p.getNombre());
                    dto.setPacienteApellido(p.getApellido());
                    dto.setPacienteDni(p.getDni());
                    dto.setPacienteTelefono(p.getTelefono());
                    dto.setPacienteCorreo(p.getCorreo());
                }

                TipoTerapia tt = t.getTipoTerapia();
                if (tt != null) {
                    dto.setTipoTerapiaKey(tt.getKey());
                    dto.setTipoTerapiaNombre(tt.getNombre());
                }
            }
        }

        return dto;
    }
}
