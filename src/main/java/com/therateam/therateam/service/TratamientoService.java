package com.therateam.therateam.service;

import com.therateam.therateam.dto.SesionDTO;
import com.therateam.therateam.dto.TratamientoDTO;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.SesionRepository;
import com.therateam.therateam.repository.TratamientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TratamientoService {

    private final TratamientoRepository repository;
    private final SesionRepository sesionRepository;

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findAllPaged(Pageable pageable) {
        return repository.findAll(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findByPacientePaged(Long pacienteId, Pageable pageable) {
        return repository.findByPacienteId(pacienteId, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findByTerapeutaPaged(Long terapeutaId, Pageable pageable) {
        return repository.findByTerapeutaId(terapeutaId, pageable).map(this::toDTO);
    }

    public List<Tratamiento> findAll() { return repository.findAll(); }
    public Optional<Tratamiento> findById(Long id) { return repository.findById(id); }
    public List<Tratamiento> findByPaciente(Long pacienteId) { return repository.findByPacienteId(pacienteId); }
    public List<Tratamiento> findByTerapeuta(Long terapeutaId) { return repository.findByTerapeutaId(terapeutaId); }
    public Tratamiento save(Tratamiento t) { return repository.save(t); }

    public Optional<Tratamiento> update(Long id, Tratamiento data) {
        return repository.findById(id).map(e -> {
            e.setPaciente(data.getPaciente());
            e.setTerapeuta(data.getTerapeuta());
            e.setTipoTerapia(data.getTipoTerapia());
            e.setNombre(data.getNombre());
            e.setTotalSesiones(data.getTotalSesiones());
            e.setPrecioPorSesion(data.getPrecioPorSesion());
            e.setEstado(data.getEstado());
            e.setSesionesAtendidas(data.getSesionesAtendidas());
            e.setTotalCobrado(data.getTotalCobrado());
            e.setSaldoAFavor(data.getSaldoAFavor());
            e.setFechaInicio(data.getFechaInicio());
            e.setNotas(data.getNotas());
            return repository.save(e);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    @Transactional(readOnly = true)
    public List<SesionDTO> getSesionesByTratamiento(Long tratamientoId) {
        return sesionRepository.findByTratamientoIdWithCita(tratamientoId)
                .stream()
                .map(this::toSesionDTO)
                .collect(Collectors.toList());
    }

    public TratamientoDTO toDTO(Tratamiento t) {
        TratamientoDTO dto = new TratamientoDTO();
        dto.setId(t.getId());
        dto.setNombre(t.getNombre());
        dto.setTotalSesiones(t.getTotalSesiones());
        dto.setSesionesAtendidas(t.getSesionesAtendidas());
        dto.setSesionesPendientes(t.getSesionesPendientes());
        dto.setMontoTotal(t.getMontoTotal());
        dto.setPrecioPorSesion(t.getPrecioPorSesion());
        dto.setTotalCobrado(t.getTotalCobrado());
        dto.setSaldoAFavor(t.getSaldoAFavor());
        dto.setFechaInicio(t.getFechaInicio());
        dto.setNotas(t.getNotas());

        if (t.getPaciente() != null) {
            Paciente p = t.getPaciente();
            dto.setPacienteId(p.getId());
            dto.setPacienteNombre(p.getNombre());
            dto.setPacienteApellido(p.getApellido());
            dto.setPacienteDni(p.getDni());
            dto.setPacienteTelefono(p.getTelefono());
        }

        if (t.getTerapeuta() != null && t.getTerapeuta().getUsuario() != null) {
            dto.setTerapeutaId(t.getTerapeuta().getId());
            Usuario u = t.getTerapeuta().getUsuario();
            dto.setTerapeutaNombre(u.getNombre() + " " + u.getApellido());
        }

        if (t.getTipoTerapia() != null) {
            dto.setTipoTerapiaKey(t.getTipoTerapia().getKey());
            dto.setTipoTerapiaNombre(t.getTipoTerapia().getNombre());
        }

        if (t.getEstado() != null) {
            dto.setEstadoKey(t.getEstado().getKey());
            dto.setEstadoNombre(t.getEstado().getNombre());
            dto.setEstadoColor(t.getEstado().getColorHex());
        }

        return dto;
    }

    private SesionDTO toSesionDTO(Sesion s) {
        SesionDTO dto = new SesionDTO();
        dto.setId(s.getId());
        dto.setNumero(s.getNumero());

        if (s.getEstado() != null) {
            SesionDTO.EstadoInfo e = new SesionDTO.EstadoInfo();
            e.setId(s.getEstado().getId());
            e.setKey(s.getEstado().getKey());
            e.setNombre(s.getEstado().getNombre());
            e.setColorHex(s.getEstado().getColorHex());
            dto.setEstado(e);
        }

        Cita cita = s.getCitaActiva();
        if (cita != null) {
            SesionDTO.CitaActivaInfo ca = new SesionDTO.CitaActivaInfo();
            ca.setId(cita.getId());
            ca.setFechaInicio(cita.getFechaInicio());
            ca.setDuracionMinutos(cita.getDuracionMinutos());

            if (cita.getEstado() != null) {
                SesionDTO.EstadoInfo ce = new SesionDTO.EstadoInfo();
                ce.setId(cita.getEstado().getId());
                ce.setKey(cita.getEstado().getKey());
                ce.setNombre(cita.getEstado().getNombre());
                ce.setColorHex(cita.getEstado().getColorHex());
                ca.setEstado(ce);
            }

            if (cita.getModalidad() != null) {
                SesionDTO.ModalidadInfo m = new SesionDTO.ModalidadInfo();
                m.setKey(cita.getModalidad().getKey());
                m.setNombre(cita.getModalidad().getNombre());
                ca.setModalidad(m);
            }

            dto.setCitaActiva(ca);
        }

        return dto;
    }
}
