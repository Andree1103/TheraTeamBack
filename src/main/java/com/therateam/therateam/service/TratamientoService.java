package com.therateam.therateam.service;

import com.therateam.therateam.dto.SesionDTO;
import com.therateam.therateam.dto.TratamientoCoberturaDTO;
import com.therateam.therateam.dto.TratamientoDTO;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.PagoRepository;
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
    private final PagoRepository pagoRepository;

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findAllPaged(Pageable pageable, String paciente, String terapeuta,
                                              Long tipoTerapiaId, String estado) {
        return repository.findAllProjected(blankToNull(paciente), blankToNull(terapeuta),
                tipoTerapiaId, blankToNull(estado), pageable);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findByPacientePaged(Long pacienteId, Pageable pageable) {
        return repository.findByPacienteIdProjected(pacienteId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findByTerapeutaPaged(Long terapeutaId, Pageable pageable) {
        return repository.findByTerapeutaIdProjected(terapeutaId, pageable);
    }

    public List<Tratamiento> findAll() { return repository.findAll(); }
    public Optional<Tratamiento> findById(Long id) { return repository.findById(id); }
    public List<Tratamiento> findByPaciente(Long pacienteId) { return repository.findByPacienteId(pacienteId); }
    public List<Tratamiento> findByTerapeuta(Long terapeutaId) { return repository.findByTerapeutaId(terapeutaId); }
    public Tratamiento save(Tratamiento t) { return repository.save(t); }

    /**
     * El formulario de edición solo expone paciente/terapeuta/tipo/estado/sesiones/precio/fecha/notas
     * — sesionesAtendidas, totalCobrado y saldoAFavor NO vienen en ese request y los maneja la propia
     * app (al completar sesiones o registrar pagos), así que no se tocan aquí: sobrescribirlos con lo
     * que traiga (o no traiga) el body los dejaría en null y viola la restricción NOT NULL de la BD.
     */
    public Optional<Tratamiento> update(Long id, Tratamiento data) {
        return repository.findById(id).map(e -> {
            e.setPaciente(data.getPaciente());
            e.setTerapeuta(data.getTerapeuta());
            e.setTipoTerapia(data.getTipoTerapia());
            e.setNombre(data.getNombre());
            e.setTotalSesiones(data.getTotalSesiones());
            e.setPrecioPorSesion(data.getPrecioPorSesion());
            e.setEstado(data.getEstado());
            e.setFechaInicio(data.getFechaInicio());
            e.setNotas(data.getNotas());
            return repository.save(e);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        if (!pagoRepository.findByTratamientoId(id).isEmpty()) {
            throw new IllegalArgumentException("No se puede eliminar: este paquete ya tiene pagos registrados. Elimina primero esos pagos.");
        }
        if (!sesionRepository.findByTratamientoId(id).isEmpty()) {
            throw new IllegalArgumentException("No se puede eliminar: este paquete ya tiene sesiones/citas creadas.");
        }
        repository.deleteById(id);
        return true;
    }

    /**
     * Cuántas sesiones ya tienen cita creada (de las planificadas) y cuántas de esas
     * ya están pagadas vs pendientes de pago. Se calcula solo cuando se pide (no en el
     * listado paginado) para no cargarle joins a la consulta pesada.
     */
    @Transactional(readOnly = true)
    public TratamientoCoberturaDTO cobertura(Long tratamientoId) {
        List<Sesion> sesiones = sesionRepository.findByTratamientoId(tratamientoId);
        int creadas = sesiones.size();
        int pagadas = (int) sesiones.stream()
                .filter(s -> s.getCitaActiva() != null
                        && s.getCitaActiva().getEstadoPago() != null
                        && "PAGADA".equals(s.getCitaActiva().getEstadoPago().getKey()))
                .count();
        return new TratamientoCoberturaDTO(creadas, pagadas, creadas - pagadas);
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
        dto.setFechaInicio(t.getFechaInicio());
        dto.setNotas(t.getNotas());

        if (t.getPaciente() != null) {
            Paciente p = t.getPaciente();
            dto.setPacienteId(p.getId());
            dto.setPacienteNombre(p.getNombre());
            dto.setPacienteApellido(p.getApellido());
            dto.setPacienteDni(p.getDni());
            dto.setPacienteTelefono(p.getTelefono());
            // El saldo a favor es del paciente, no de este paquete puntual (ver PagoService).
            dto.setSaldoAFavor(p.getSaldoAFavor());
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

            if (cita.getEstadoPago() != null) {
                ca.setEstadoPagoKey(cita.getEstadoPago().getKey());
                ca.setEstadoPagoNombre(cita.getEstadoPago().getNombre());
                ca.setEstadoPagoColor(cita.getEstadoPago().getColor());
            }

            ca.setPrecio(cita.getPrecio());
            ca.setMontoPagado(cita.getMontoPagado());

            dto.setCitaActiva(ca);
        }

        return dto;
    }
}
