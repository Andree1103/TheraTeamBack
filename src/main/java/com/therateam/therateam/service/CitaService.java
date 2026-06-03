package com.therateam.therateam.service;

import com.therateam.therateam.dto.CitaConPacienteRequest;
import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.dto.CitaRapidaRequest;
import com.therateam.therateam.dto.PacienteInput;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.*;
import com.therateam.therateam.specification.CitaSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CitaService {

    private final CitaRepository citaRepository;
    private final PacienteRepository pacienteRepository;
    private final TerapeutaRepository terapeutaRepository;
    private final TipoTerapiaRepository tipoTerapiaRepository;
    private final TratamientoRepository tratamientoRepository;
    private final SesionRepository sesionRepository;
    private final CatEstadoTratamientoRepository catEstadoTratamientoRepository;
    private final CatEstadoSesionRepository catEstadoSesionRepository;
    private final CatEstadoCitaRepository catEstadoCitaRepository;
    private final CatModalidadRepository catModalidadRepository;

    public List<CitaDTO> findAll() {
        return citaRepository.findAll().stream().map(this::toDTO).toList();
    }

    public Page<CitaDTO> findAllPaged(Pageable pageable) {
        return citaRepository.findAll(pageable).map(this::toDTO);
    }

    public Page<CitaDTO> findByFiltrosPaged(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                             String terapeuta, Pageable pageable) {
        return citaRepository.findAll(CitaSpecification.byFiltros(fechaInicio, fechaFin, terapeuta), pageable)
                .map(this::toDTO);
    }

    public List<CitaDTO> findByFiltros(LocalDateTime fechaInicio, LocalDateTime fechaFin, String terapeuta) {
        return citaRepository.findAll(CitaSpecification.byFiltros(fechaInicio, fechaFin, terapeuta))
                .stream().map(this::toDTO).toList();
    }

    public Optional<CitaDTO> findById(Long id) {
        return citaRepository.findById(id).map(this::toDTO);
    }

    public Cita save(Cita cita) { return citaRepository.save(cita); }

    public Optional<Cita> update(Long id, Cita data) {
        return citaRepository.findById(id).map(e -> {
            e.setTerapeuta(data.getTerapeuta());
            e.setModalidad(data.getModalidad());
            e.setFechaInicio(data.getFechaInicio());
            e.setFechaFin(data.getFechaFin());
            e.setDuracionMinutos(data.getDuracionMinutos());
            e.setEstado(data.getEstado());
            e.setLinkVideollamada(data.getLinkVideollamada());
            e.setNotasPrevias(data.getNotasPrevias());
            e.setRecordatorioEnviado(data.getRecordatorioEnviado());
            return citaRepository.save(e);
        });
    }

    public boolean delete(Long id) {
        if (!citaRepository.existsById(id)) return false;
        citaRepository.deleteById(id);
        return true;
    }

    /**
     * Crea cita a partir de paciente_id sin necesidad de pre-crear tratamiento/sesion.
     * Flujo: busca o crea tratamiento → crea sesion → crea cita → retorna CitaDTO.
     */
    @Transactional
    public CitaDTO crearRapida(CitaRapidaRequest req) {
        Paciente paciente = pacienteRepository.findById(req.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado: " + req.getPacienteId()));

        Terapeuta terapeuta = terapeutaRepository.findById(req.getTerapeutaId())
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado: " + req.getTerapeutaId()));

        // Tratamiento: usar el indicado, o buscar uno activo, o crear uno nuevo
        Tratamiento tratamiento;
        if (req.getTratamientoId() != null) {
            tratamiento = tratamientoRepository.findById(req.getTratamientoId())
                    .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado: " + req.getTratamientoId()));
        } else {
            TipoTerapia tipoTerapia = null;
            if (req.getTipoTerapiaId() != null) {
                tipoTerapia = tipoTerapiaRepository.findById(req.getTipoTerapiaId()).orElse(null);
            }

            TipoTerapia finalTipoTerapia = tipoTerapia;
            List<Tratamiento> existentes = (tipoTerapia != null)
                    ? tratamientoRepository.findByPacienteIdAndTerapeutaIdAndTipoTerapiaId(
                            paciente.getId(), terapeuta.getId(), tipoTerapia.getId())
                    : List.of();

            tratamiento = existentes.stream()
                    .filter(t -> t.getEstado() != null && "ACTIVO".equals(t.getEstado().getKey()))
                    .findFirst()
                    .orElseGet(() -> crearTratamiento(paciente, terapeuta, finalTipoTerapia, req));
        }

        // Siguiente número de sesión
        int siguienteNumero = sesionRepository.findByTratamientoId(tratamiento.getId()).size() + 1;

        // Estado de sesión: PENDIENTE por defecto
        CatEstadoSesion estadoSesion = catEstadoSesionRepository.findByKey("PENDIENTE")
                .orElseGet(() -> catEstadoSesionRepository.findAll().stream().findFirst().orElse(null));

        Sesion sesion = new Sesion();
        sesion.setTratamiento(tratamiento);
        sesion.setNumero(siguienteNumero);
        sesion.setEstado(estadoSesion);
        sesion = sesionRepository.save(sesion);

        // Estado y modalidad de la cita
        CatEstadoCita estadoCita = null;
        if (req.getEstadoCitaId() != null) {
            estadoCita = catEstadoCitaRepository.findById(req.getEstadoCitaId()).orElse(null);
        }
        if (estadoCita == null) {
            estadoCita = catEstadoCitaRepository.findByKey("PROGRAMADA")
                    .orElseGet(() -> catEstadoCitaRepository.findAll().stream().findFirst().orElse(null));
        }

        CatModalidad modalidad = null;
        if (req.getModalidadId() != null) {
            modalidad = catModalidadRepository.findById(req.getModalidadId()).orElse(null);
        }

        Cita cita = new Cita();
        cita.setSesion(sesion);
        cita.setTerapeuta(terapeuta);
        cita.setModalidad(modalidad);
        cita.setEstado(estadoCita);
        cita.setFechaInicio(req.getFechaInicio());
        cita.setFechaFin(req.getFechaFin());
        cita.setDuracionMinutos(req.getDuracionMinutos());
        cita.setNotasPrevias(req.getNotasPrevias());
        cita.setLinkVideollamada(req.getLinkVideollamada());
        cita = citaRepository.save(cita);

        // Actualizar sesion.citaActiva apuntando a la cita recién creada
        sesion.setCitaActiva(cita);
        sesionRepository.save(sesion);

        return toDTO(cita);
    }

    /**
     * Busca paciente por DNI — si no existe lo crea. Transaccional junto con el caller.
     */
    private Paciente buscarOCrearPaciente(PacienteInput input) {
        if (input == null) return null;
        if (input.getDni() != null && !input.getDni().isBlank()) {
            Optional<Paciente> existente = pacienteRepository.findByDni(input.getDni());
            if (existente.isPresent()) return existente.get();
        }
        Paciente nuevo = new Paciente();
        nuevo.setDni(input.getDni());
        nuevo.setNombre(input.getNombre());
        nuevo.setApellido(input.getApellido());
        nuevo.setTelefono(input.getTelefono());
        nuevo.setCorreo(input.getCorreo());
        return pacienteRepository.save(nuevo);
    }

    /**
     * Crea una cita atómica: busca o crea paciente(s), resuelve terapeuta/tipoTerapia por
     * nombre/key, genera tratamiento + sesion + cita en una sola transacción.
     * Retorna lista porque soporta sesiones multipaciente (paciente2 opcional).
     */
    @Transactional
    public List<CitaDTO> crearConPaciente(CitaConPacienteRequest req) {
        // Resolver terapeuta por nombre
        Terapeuta terapeuta = null;
        if (req.getTerapeutaNombre() != null && !req.getTerapeutaNombre().isBlank()) {
            List<Terapeuta> matches = terapeutaRepository.findByNombreCompleto(req.getTerapeutaNombre());
            if (!matches.isEmpty()) {
                terapeuta = matches.get(0);
            } else {
                throw new IllegalArgumentException("Terapeuta no encontrado: '" + req.getTerapeutaNombre() + "'");
            }
        } else {
            throw new IllegalArgumentException("Se requiere terapeutaNombre");
        }

        // Resolver tipo de terapia por key
        TipoTerapia tipoTerapia = null;
        if (req.getTipoKey() != null) {
            tipoTerapia = tipoTerapiaRepository.findByKey(req.getTipoKey()).orElse(null);
        }

        // Resolver estado de cita por key
        CatEstadoCita estadoCita = null;
        if (req.getEstadoKey() != null) {
            estadoCita = catEstadoCitaRepository.findByKey(req.getEstadoKey()).orElse(null);
        }
        if (estadoCita == null) {
            estadoCita = catEstadoCitaRepository.findByKey("PROGRAMADA")
                    .orElseGet(() -> catEstadoCitaRepository.findAll().stream().findFirst().orElse(null));
        }

        // Resolver modalidad por key — fallback a PRESENCIAL o la primera disponible
        CatModalidad modalidad = null;
        if (req.getModalidadKey() != null) {
            modalidad = catModalidadRepository.findByKey(req.getModalidadKey()).orElse(null);
        }
        if (modalidad == null) {
            modalidad = catModalidadRepository.findByKey("PRESENCIAL")
                    .orElseGet(() -> catModalidadRepository.findAll().stream().findFirst().orElse(null));
        }

        // fechaFin = fechaInicio + duracionMinutos
        LocalDateTime fechaFin = (req.getFechaInicio() != null && req.getDuracionMinutos() != null)
                ? req.getFechaInicio().plusMinutes(req.getDuracionMinutos())
                : null;

        // Crear cita por cada paciente
        List<CitaDTO> resultado = new java.util.ArrayList<>();

        Paciente p1 = buscarOCrearPaciente(req.getPaciente());
        if (p1 != null) {
            resultado.add(crearCitaParaPaciente(p1, terapeuta, tipoTerapia, estadoCita, modalidad,
                    req.getFechaInicio(), fechaFin, req.getDuracionMinutos(), req.getObservacion()));
        }

        if (req.getPaciente2() != null) {
            Paciente p2 = buscarOCrearPaciente(req.getPaciente2());
            resultado.add(crearCitaParaPaciente(p2, terapeuta, tipoTerapia, estadoCita, modalidad,
                    req.getFechaInicio(), fechaFin, req.getDuracionMinutos(), req.getObservacion()));
        }

        return resultado;
    }

    private CitaDTO crearCitaParaPaciente(Paciente paciente, Terapeuta terapeuta,
                                           TipoTerapia tipoTerapia, CatEstadoCita estadoCita,
                                           CatModalidad modalidad, LocalDateTime fechaInicio,
                                           LocalDateTime fechaFin, Integer duracionMinutos,
                                           String observacion) {
        // Buscar tratamiento activo o crear uno nuevo
        List<Tratamiento> existentes = (terapeuta != null && tipoTerapia != null)
                ? tratamientoRepository.findByPacienteIdAndTerapeutaIdAndTipoTerapiaId(
                        paciente.getId(), terapeuta.getId(), tipoTerapia.getId())
                : List.of();

        Terapeuta finalTerapeuta = terapeuta;
        TipoTerapia finalTipoTerapia = tipoTerapia;

        Tratamiento tratamiento = existentes.stream()
                .filter(t -> t.getEstado() != null && "ACTIVO".equals(t.getEstado().getKey()))
                .findFirst()
                .orElseGet(() -> {
                    CitaRapidaRequest dummy = new CitaRapidaRequest();
                    dummy.setNombreTratamiento(finalTipoTerapia != null ? finalTipoTerapia.getNombre() : "Tratamiento");
                    dummy.setTotalSesiones(1);
                    dummy.setPrecioPorSesion(BigDecimal.ZERO);
                    return crearTratamiento(paciente, finalTerapeuta, finalTipoTerapia, dummy);
                });

        // Siguiente sesión
        int siguienteNumero = sesionRepository.findByTratamientoId(tratamiento.getId()).size() + 1;

        CatEstadoSesion estadoSesion = catEstadoSesionRepository.findByKey("PENDIENTE")
                .orElseGet(() -> catEstadoSesionRepository.findAll().stream().findFirst().orElse(null));

        Sesion sesion = new Sesion();
        sesion.setTratamiento(tratamiento);
        sesion.setNumero(siguienteNumero);
        sesion.setEstado(estadoSesion);
        sesion = sesionRepository.save(sesion);

        Cita cita = new Cita();
        cita.setSesion(sesion);
        cita.setTerapeuta(terapeuta);
        cita.setModalidad(modalidad);
        cita.setEstado(estadoCita);
        cita.setFechaInicio(fechaInicio);
        cita.setFechaFin(fechaFin);
        cita.setDuracionMinutos(duracionMinutos);
        if (observacion != null) cita.setNotasPrevias(observacion);
        cita = citaRepository.save(cita);

        sesion.setCitaActiva(cita);
        sesionRepository.save(sesion);

        return toDTO(cita);
    }

    private Tratamiento crearTratamiento(Paciente paciente, Terapeuta terapeuta,
                                         TipoTerapia tipoTerapia, CitaRapidaRequest req) {
        CatEstadoTratamiento estadoActivo = catEstadoTratamientoRepository.findByKey("ACTIVO")
                .orElseGet(() -> catEstadoTratamientoRepository.findAll().stream().findFirst().orElse(null));

        Tratamiento t = new Tratamiento();
        t.setPaciente(paciente);
        t.setTerapeuta(terapeuta);
        t.setTipoTerapia(tipoTerapia);
        t.setEstado(estadoActivo);
        t.setFechaInicio(LocalDate.now());
        t.setNombre(req.getNombreTratamiento() != null ? req.getNombreTratamiento()
                : (tipoTerapia != null ? tipoTerapia.getNombre() : "Tratamiento"));
        t.setTotalSesiones(req.getTotalSesiones() != null ? req.getTotalSesiones() : 1);
        t.setPrecioPorSesion(req.getPrecioPorSesion() != null ? req.getPrecioPorSesion() : BigDecimal.ZERO);
        return tratamientoRepository.save(t);
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

        if (c.getEstado() != null) {
            dto.setEstado(c.getEstado().getKey());
            dto.setEstadoNombre(c.getEstado().getNombre());
            dto.setEstadoColor(c.getEstado().getColorHex());
        }

        if (c.getModalidad() != null) {
            dto.setModalidad(c.getModalidad().getKey());
        }

        if (c.getTerapeuta() != null && c.getTerapeuta().getUsuario() != null) {
            Usuario u = c.getTerapeuta().getUsuario();
            dto.setTerapeutaNombre(u.getNombre() + " " + u.getApellido());
            dto.setTerapeutaId(c.getTerapeuta().getId());
        }

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
