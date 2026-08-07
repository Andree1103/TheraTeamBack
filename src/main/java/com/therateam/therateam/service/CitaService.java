package com.therateam.therateam.service;

import com.therateam.therateam.dto.CitaConPacienteRequest;
import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.dto.CitaRapidaRequest;
import com.therateam.therateam.dto.PacienteInput;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CitaService {

    private final CitaRepository citaRepository;
    private final PacienteRepository pacienteRepository;
    private final PacienteService pacienteService;
    private final TerapeutaRepository terapeutaRepository;
    private final TipoTerapiaRepository tipoTerapiaRepository;
    private final TratamientoRepository tratamientoRepository;
    private final SesionRepository sesionRepository;
    private final CatEstadoTratamientoRepository catEstadoTratamientoRepository;
    private final CatEstadoSesionRepository catEstadoSesionRepository;
    private final CatEstadoCitaRepository catEstadoCitaRepository;
    private final CatModalidadRepository catModalidadRepository;
    private final CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;
    private final PagoRepository pagoRepository;
    private final CatMetodoPagoRepository catMetodoPagoRepository;
    private final DisponibilidadService disponibilidadService;

    public List<CitaDTO> findAll() {
        return citaRepository.findAllProjected(org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    /** `terapeutaIdRestriccion` acota los resultados a un solo terapeuta (citasSoloPropias=true); null = sin restricción. */
    public Page<CitaDTO> findAllPaged(Pageable pageable, Long terapeutaIdRestriccion) {
        if (terapeutaIdRestriccion == null) return citaRepository.findAllProjected(pageable);
        return citaRepository.findByFiltrosProjected(null, null, null, terapeutaIdRestriccion, pageable);
    }

    public Page<CitaDTO> findByFiltrosPaged(LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                             String terapeuta, Long terapeutaIdRestriccion, Pageable pageable) {
        String terapeutaFiltro = (terapeuta == null || terapeuta.isBlank()) ? null : terapeuta.toLowerCase();
        return citaRepository.findByFiltrosProjected(fechaInicio, fechaFin, terapeutaFiltro, terapeutaIdRestriccion, pageable);
    }

    public List<CitaDTO> findByFiltros(LocalDateTime fechaInicio, LocalDateTime fechaFin, String terapeuta) {
        String terapeutaFiltro = (terapeuta == null || terapeuta.isBlank()) ? null : terapeuta.toLowerCase();
        return citaRepository.findByFiltrosProjected(fechaInicio, fechaFin, terapeutaFiltro, null,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    public Optional<CitaDTO> findById(Long id) {
        return citaRepository.findByIdProjected(id);
    }

    public List<CitaDTO> findByPaciente(Long pacienteId) {
        return citaRepository.findByPacienteIdProjected(pacienteId);
    }

    public Cita save(Cita cita) { return citaRepository.save(cita); }

    public Optional<Cita> update(Long id, Cita data) {
        return citaRepository.findById(id).map(e -> {
            Long terapeutaOriginalId = e.getTerapeuta() != null ? e.getTerapeuta().getId() : null;
            LocalDateTime inicioOriginal = e.getFechaInicio();
            LocalDateTime finOriginal = e.getFechaFin();

            if (data.getPaciente() != null) e.setPaciente(data.getPaciente());
            e.setTerapeuta(data.getTerapeuta());
            e.setModalidad(data.getModalidad());
            e.setFechaInicio(data.getFechaInicio());
            e.setFechaFin(data.getFechaFin());
            e.setDuracionMinutos(data.getDuracionMinutos());
            e.setEstado(data.getEstado());
            e.setLinkVideollamada(data.getLinkVideollamada());
            e.setNotasPrevias(data.getNotasPrevias());
            e.setRecordatorioEnviado(data.getRecordatorioEnviado());
            if (data.getTipoRecurrencia() != null) e.setTipoRecurrencia(data.getTipoRecurrencia());

            TipoTerapia tipo = e.getTipoTerapia() != null ? e.getTipoTerapia()
                    : (e.getSesion() != null && e.getSesion().getTratamiento() != null
                        ? e.getSesion().getTratamiento().getTipoTerapia() : null);
            Integer maxPacientes = tipo != null ? tipo.getMaxPacientes() : null;

            // Si ni el terapeuta ni el horario cambiaron, no repetir la validación de disponibilidad:
            // de lo contrario, una cita antigua cuyo horario original ya no calza con el horario
            // ACTUAL del terapeuta (porque se lo cambiaron después de agendarla) quedaría imposible
            // de editar para siempre, incluso para cambios que no tocan fecha/hora/terapeuta.
            Long terapeutaNuevoId = e.getTerapeuta() != null ? e.getTerapeuta().getId() : null;
            boolean sinCambioDeHorario = java.util.Objects.equals(terapeutaOriginalId, terapeutaNuevoId)
                    && java.util.Objects.equals(inicioOriginal, e.getFechaInicio())
                    && java.util.Objects.equals(finOriginal, e.getFechaFin());
            if (!sinCambioDeHorario) {
                validarDisponibilidad(e.getTerapeuta(), e.getFechaInicio(), e.getFechaFin(), maxPacientes, id);
            }
            validarPacienteDisponible(e.getPaciente(), e.getFechaInicio(), e.getFechaFin(), id);

            return citaRepository.save(e);
        });
    }

    public boolean delete(Long id) {
        if (!citaRepository.existsById(id)) return false;
        citaRepository.deleteById(id);
        return true;
    }

    /**
     * Si el paquete ya tiene cobrado (por un pago anticipado, total o parcial) lo suficiente
     * para cubrir la sesión número `numeroSesion`, la cita nace PAGADA sin necesidad de un
     * Pago nuevo — el dinero ya entró antes, esto solo refleja que esa sesión puntual ya
     * está cubierta por el precio total del paquete pagado hasta ahora.
     */
    private CatEstadoPagoCita estadoPagoAutomatico(Tratamiento tratamiento, int numeroSesion) {
        BigDecimal precio = tratamiento.getPrecioPorSesion() != null ? tratamiento.getPrecioPorSesion() : BigDecimal.ZERO;
        BigDecimal totalCobrado = tratamiento.getTotalCobrado() != null ? tratamiento.getTotalCobrado() : BigDecimal.ZERO;
        int sesionesCubiertas = precio.compareTo(BigDecimal.ZERO) > 0
                ? totalCobrado.divide(precio, 0, RoundingMode.DOWN).intValue()
                : 0;

        // No comparamos por número de sesión (una sesión anterior pudo quedar SIN_PAGO por su
        // cuenta, ej. porque se registró antes de que llegara este pago) — contamos cuántos
        // "cupos" pagados ya se usaron y si queda alguno libre para esta sesión nueva.
        long cuposYaUsados = sesionRepository.findByTratamientoId(tratamiento.getId()).stream()
                .filter(s -> s.getNumero() != null && s.getNumero() < numeroSesion)
                .filter(s -> s.getCitaActiva() != null && s.getCitaActiva().getEstadoPago() != null
                        && "PAGADA".equals(s.getCitaActiva().getEstadoPago().getKey()))
                .count();

        String key = cuposYaUsados < sesionesCubiertas ? "PAGADA" : "SIN_PAGO";
        return catEstadoPagoCitaRepository.findByKey(key).orElse(null);
    }

    /** Actualiza solo el estado_pago de una cita por key (SIN_PAGO, PARCIAL, PAGADA) */
    public Optional<CitaDTO> actualizarEstadoPago(Long id, String key) {
        return citaRepository.findById(id).map(cita -> {
            CatEstadoPagoCita estadoPago = catEstadoPagoCitaRepository.findByKey(key).orElse(null);
            cita.setEstadoPago(estadoPago);
            return toDTO(citaRepository.save(cita));
        });
    }

    /**
     * Crea cita a partir de paciente_id. Si se indica tratamientoId (paquete elegido
     * explícitamente), engancha la cita a la siguiente sesión de ese paquete. Si no,
     * es una cita normal e independiente — NO se crea ningún paquete/tratamiento.
     */
    @Transactional
    public CitaDTO crearRapida(CitaRapidaRequest req) {
        validarFechaNoPasada(req.getFechaInicio());

        Paciente paciente = pacienteRepository.findById(req.getPacienteId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado: " + req.getPacienteId()));

        Terapeuta terapeuta = terapeutaRepository.findById(req.getTerapeutaId())
                .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado: " + req.getTerapeutaId()));

        TipoTerapia tipoTerapiaDirecta = null;
        if (req.getTipoTerapiaId() != null) {
            tipoTerapiaDirecta = tipoTerapiaRepository.findById(req.getTipoTerapiaId()).orElse(null);
        }

        Tratamiento tratamiento = null;
        Sesion sesion = null;
        int siguienteNumero = 0;

        if (req.getTratamientoId() != null) {
            tratamiento = tratamientoRepository.findById(req.getTratamientoId())
                    .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado: " + req.getTratamientoId()));

            siguienteNumero = sesionRepository.findByTratamientoId(tratamiento.getId()).size() + 1;
            CatEstadoSesion estadoSesion = catEstadoSesionRepository.findByKey("PENDIENTE")
                    .orElseGet(() -> catEstadoSesionRepository.findAll().stream().findFirst().orElse(null));
            sesion = new Sesion();
            sesion.setTratamiento(tratamiento);
            sesion.setNumero(siguienteNumero);
            sesion.setEstado(estadoSesion);
            sesion = sesionRepository.save(sesion);
        }

        TipoTerapia tipoParaCapacidad = tratamiento != null && tratamiento.getTipoTerapia() != null
                ? tratamiento.getTipoTerapia() : tipoTerapiaDirecta;
        validarDisponibilidad(terapeuta, req.getFechaInicio(), req.getFechaFin(),
                tipoParaCapacidad != null ? tipoParaCapacidad.getMaxPacientes() : null, null);
        validarPacienteDisponible(paciente, req.getFechaInicio(), req.getFechaFin(), null);

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

        BigDecimal precio = req.getPrecioPorSesion() != null ? req.getPrecioPorSesion()
                : (tratamiento != null ? tratamiento.getPrecioPorSesion() : null);

        // Estado de pago inicial: PAGADA si el paquete ya cubre esta sesión (pago anticipado);
        // si es cita normal (sin paquete), SIN_PAGO (se actualiza igual si crearPago=true y pagadoInmediato=true).
        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setTipoTerapia(tipoParaCapacidad);
        cita.setPrecio(precio);
        cita.setSesion(sesion);
        cita.setTerapeuta(terapeuta);
        cita.setModalidad(modalidad);
        cita.setEstado(estadoCita);
        cita.setEstadoPago(tratamiento != null
                ? estadoPagoAutomatico(tratamiento, siguienteNumero)
                : catEstadoPagoCitaRepository.findByKey("SIN_PAGO").orElse(null));
        cita.setFechaInicio(req.getFechaInicio());
        cita.setFechaFin(req.getFechaFin());
        cita.setDuracionMinutos(req.getDuracionMinutos());
        cita.setNotasPrevias(req.getNotasPrevias());
        cita.setLinkVideollamada(req.getLinkVideollamada());
        cita.setTipoRecurrencia(req.getTipoRecurrencia() != null ? req.getTipoRecurrencia() : "EVENTUAL");
        cita = citaRepository.save(cita);

        if (sesion != null) {
            sesion.setCitaActiva(cita);
            sesionRepository.save(sesion);
        }

        // Crear pago si se solicitó
        if (req.isCrearPago()) {
            cita = crearPagoParaCita(cita, tratamiento, paciente, req);
        }

        return toDTO(cita);
    }

    /**
     * Crea el pago vinculado a la cita y actualiza su estado_pago. `tratamiento` puede ser
     * null (cita normal, sin paquete) — en ese caso el pago solo marca esta cita puntual,
     * sin tocar ningún tratamiento.
     */
    private Cita crearPagoParaCita(Cita cita, Tratamiento tratamiento, Paciente paciente,
                                    CitaRapidaRequest req) {
        BigDecimal monto = req.getPrecioCita() != null
                ? req.getPrecioCita()
                : (cita.getPrecio() != null ? cita.getPrecio()
                    : (tratamiento != null && tratamiento.getPrecioPorSesion() != null
                        ? tratamiento.getPrecioPorSesion() : BigDecimal.ZERO));

        CatMetodoPago metodo = null;
        if (req.getMetodoPagoId() != null) {
            metodo = catMetodoPagoRepository.findById(req.getMetodoPagoId()).orElse(null);
        }

        Pago pago = new Pago();
        pago.setTratamiento(tratamiento);
        pago.setPaciente(paciente);
        pago.setCita(cita);
        pago.setMetodo(metodo);
        pago.setMontoRecibido(monto);
        pago.setMontoAplicado(monto);
        pago.setSaldoGenerado(BigDecimal.ZERO);
        pago.setSaldoPrevio(BigDecimal.ZERO);
        pagoRepository.save(pago);

        if (tratamiento != null) {
            BigDecimal totalCobradoNuevo = (tratamiento.getTotalCobrado() != null
                    ? tratamiento.getTotalCobrado()
                    : BigDecimal.ZERO).add(monto);
            tratamiento.setTotalCobrado(totalCobradoNuevo);
            tratamientoRepository.save(tratamiento);
        }

        // Actualizar estado_pago de la cita
        String keyEstadoPago = req.isPagadoInmediato() ? "PAGADA" : "SIN_PAGO";
        CatEstadoPagoCita estadoPago = catEstadoPagoCitaRepository.findByKey(keyEstadoPago).orElse(null);
        cita.setEstadoPago(estadoPago);
        return citaRepository.save(cita);
    }

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
        // Pasa por PacienteService (no el repository directo) para que también se le cree su cuenta de acceso.
        return pacienteService.save(nuevo);
    }

    @Transactional
    public List<CitaDTO> crearConPaciente(CitaConPacienteRequest req) {
        validarFechaNoPasada(req.getFechaInicio());

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

        TipoTerapia tipoTerapia = null;
        if (req.getTipoKey() != null) {
            tipoTerapia = tipoTerapiaRepository.findByKey(req.getTipoKey()).orElse(null);
        }

        CatEstadoCita estadoCita = null;
        if (req.getEstadoKey() != null) {
            estadoCita = catEstadoCitaRepository.findByKey(req.getEstadoKey()).orElse(null);
        }
        if (estadoCita == null) {
            estadoCita = catEstadoCitaRepository.findByKey("PROGRAMADA")
                    .orElseGet(() -> catEstadoCitaRepository.findAll().stream().findFirst().orElse(null));
        }

        CatModalidad modalidad = null;
        if (req.getModalidadKey() != null) {
            modalidad = catModalidadRepository.findByKey(req.getModalidadKey()).orElse(null);
        }
        if (modalidad == null) {
            modalidad = catModalidadRepository.findByKey("PRESENCIAL")
                    .orElseGet(() -> catModalidadRepository.findAll().stream().findFirst().orElse(null));
        }

        LocalDateTime fechaFin = (req.getFechaInicio() != null && req.getDuracionMinutos() != null)
                ? req.getFechaInicio().plusMinutes(req.getDuracionMinutos())
                : null;

        List<CitaDTO> resultado = new java.util.ArrayList<>();

        String tipoRecurrencia = req.getTipoRecurrencia() != null ? req.getTipoRecurrencia() : "EVENTUAL";

        Paciente p1 = buscarOCrearPaciente(req.getPaciente());
        if (p1 != null) {
            resultado.add(crearCitaParaPaciente(p1, terapeuta, tipoTerapia, estadoCita, modalidad,
                    req.getFechaInicio(), fechaFin, req.getDuracionMinutos(), req.getObservacion(),
                    req.getPrecioPorSesion(), req.getTratamientoId(), tipoRecurrencia));
        }

        if (req.getPaciente2() != null) {
            Paciente p2 = buscarOCrearPaciente(req.getPaciente2());
            resultado.add(crearCitaParaPaciente(p2, terapeuta, tipoTerapia, estadoCita, modalidad,
                    req.getFechaInicio(), fechaFin, req.getDuracionMinutos(), req.getObservacion(),
                    req.getPrecioPorSesion(), null, tipoRecurrencia));
        }

        return resultado;
    }

    private CitaDTO crearCitaParaPaciente(Paciente paciente, Terapeuta terapeuta,
                                           TipoTerapia tipoTerapia, CatEstadoCita estadoCita,
                                           CatModalidad modalidad, LocalDateTime fechaInicio,
                                           LocalDateTime fechaFin, Integer duracionMinutos,
                                           String observacion,
                                           BigDecimal precioPorSesion,
                                           Long tratamientoIdExplicito,
                                           String tipoRecurrencia) {
        validarDisponibilidad(terapeuta, fechaInicio, fechaFin,
                tipoTerapia != null ? tipoTerapia.getMaxPacientes() : null, null);
        validarPacienteDisponible(paciente, fechaInicio, fechaFin, null);

        Tratamiento tratamiento = null;
        Sesion sesion = null;
        int siguienteNumero = 0;

        if (tratamientoIdExplicito != null) {
            // Paquete existente elegido explícitamente: se usa tal cual, sin crear nada nuevo.
            tratamiento = tratamientoRepository.findById(tratamientoIdExplicito)
                    .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado: " + tratamientoIdExplicito));
            if (tratamiento.getPaciente() == null || !tratamiento.getPaciente().getId().equals(paciente.getId())) {
                throw new IllegalArgumentException("El tratamiento seleccionado no pertenece a este paciente");
            }
            siguienteNumero = sesionRepository.findByTratamientoId(tratamiento.getId()).size() + 1;
            CatEstadoSesion estadoSesion = catEstadoSesionRepository.findByKey("PENDIENTE")
                    .orElseGet(() -> catEstadoSesionRepository.findAll().stream().findFirst().orElse(null));
            sesion = new Sesion();
            sesion.setTratamiento(tratamiento);
            sesion.setNumero(siguienteNumero);
            sesion.setEstado(estadoSesion);
            sesion = sesionRepository.save(sesion);
        }
        // Sin paquete: cada cita es normal e independiente — NO se crea ningún tratamiento.
        // Al atenderse, esta cita se registra como AtencionClinica, no como Sesion de un paquete.

        BigDecimal precio = precioPorSesion != null ? precioPorSesion
                : (tratamiento != null ? tratamiento.getPrecioPorSesion() : null);

        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setTipoTerapia(tipoTerapia);
        cita.setPrecio(precio);
        cita.setSesion(sesion);
        cita.setTerapeuta(terapeuta);
        cita.setModalidad(modalidad);
        cita.setEstado(estadoCita);
        cita.setEstadoPago(tratamiento != null
                ? estadoPagoAutomatico(tratamiento, siguienteNumero)
                : catEstadoPagoCitaRepository.findByKey("SIN_PAGO").orElse(null));
        cita.setFechaInicio(fechaInicio);
        cita.setFechaFin(fechaFin);
        cita.setDuracionMinutos(duracionMinutos);
        if (observacion != null) cita.setNotasPrevias(observacion);
        cita.setTipoRecurrencia(tipoRecurrencia != null ? tipoRecurrencia : "EVENTUAL");
        cita = citaRepository.save(cita);

        if (sesion != null) {
            sesion.setCitaActiva(cita);
            sesionRepository.save(sesion);
        }

        return toDTO(cita);
    }

    /**
     * Valida que [inicio, fin) esté dentro del horario del terapeuta (y no bloqueado por
     * una excepción) y que no supere el cupo simultáneo (maxPacientes) del tipo de terapia.
     * Lanza IllegalArgumentException (→ 400) si no cumple.
     */
    /** No se pueden programar citas nuevas en una fecha/hora que ya pasó. */
    private void validarFechaNoPasada(LocalDateTime fechaInicio) {
        if (fechaInicio != null && fechaInicio.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se puede crear una cita en una fecha u hora pasada.");
        }
    }

    private void validarDisponibilidad(Terapeuta terapeuta, LocalDateTime inicio, LocalDateTime fin,
                                        Integer maxPacientes, Long excluirCitaId) {
        if (terapeuta == null || terapeuta.getId() == null || inicio == null || fin == null) return;

        if (!disponibilidadService.estaDentroDeHorario(terapeuta.getId(), inicio, fin)) {
            throw new IllegalArgumentException(
                    "El terapeuta no atiende en ese horario (fuera de su horario habitual o bloqueado por una excepción).");
        }

        int capacidad = (maxPacientes != null && maxPacientes > 0) ? maxPacientes : 1;
        List<Cita> solapadas = excluirCitaId != null
                ? citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotAndIdNot(
                        terapeuta.getId(), fin, inicio, "CANCELADA", excluirCitaId)
                : citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNot(
                        terapeuta.getId(), fin, inicio, "CANCELADA");
        if (solapadas.size() >= capacidad) {
            throw new IllegalArgumentException("El terapeuta ya tiene el cupo completo en ese horario.");
        }
    }

    /**
     * Un paciente no puede tener dos citas activas que se solapen en el tiempo — no puede estar
     * en dos sesiones a la vez, sin importar si son con el mismo terapeuta o con otro distinto.
     */
    private void validarPacienteDisponible(Paciente paciente, LocalDateTime inicio, LocalDateTime fin, Long excluirCitaId) {
        if (paciente == null || paciente.getId() == null || inicio == null || fin == null) return;

        List<Cita> solapadas = excluirCitaId != null
                ? citaRepository.findByPacienteIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotAndIdNot(
                        paciente.getId(), fin, inicio, "CANCELADA", excluirCitaId)
                : citaRepository.findByPacienteIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNot(
                        paciente.getId(), fin, inicio, "CANCELADA");
        if (!solapadas.isEmpty()) {
            throw new IllegalArgumentException("El paciente ya tiene otra cita en ese horario — no puede estar en dos sesiones a la vez.");
        }
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
        dto.setTipoRecurrencia(c.getTipoRecurrencia());
        dto.setPrecio(c.getPrecio());
        dto.setMontoPagado(c.getMontoPagado());

        if (c.getEstado() != null) {
            dto.setEstado(c.getEstado().getKey());
            dto.setEstadoNombre(c.getEstado().getNombre());
            dto.setEstadoColor(c.getEstado().getColorHex());
        }

        if (c.getEstadoPago() != null) {
            dto.setEstadoPagoKey(c.getEstadoPago().getKey());
            dto.setEstadoPagoNombre(c.getEstadoPago().getNombre());
            dto.setEstadoPagoColor(c.getEstadoPago().getColor());
        }

        if (c.getModalidad() != null) {
            dto.setModalidad(c.getModalidad().getKey());
        }

        if (c.getTerapeuta() != null && c.getTerapeuta().getUsuario() != null) {
            Usuario u = c.getTerapeuta().getUsuario();
            dto.setTerapeutaNombre(u.getNombre() + " " + u.getApellido());
            dto.setTerapeutaId(c.getTerapeuta().getId());
        }

        // Paciente y tipo de terapia son directos de la cita — ya no dependen de un paquete.
        if (c.getPaciente() != null) {
            Paciente p = c.getPaciente();
            dto.setPacienteId(p.getId());
            dto.setPacienteNombre(p.getNombre());
            dto.setPacienteApellido(p.getApellido());
            dto.setPacienteDni(p.getDni());
            dto.setPacienteTelefono(p.getTelefono());
            dto.setPacienteCorreo(p.getCorreo());
        }

        if (c.getTipoTerapia() != null) {
            dto.setTipoTerapiaKey(c.getTipoTerapia().getKey());
            dto.setTipoTerapiaNombre(c.getTipoTerapia().getNombre());
        }

        // Sesión: solo existe si esta cita está ligada a un paquete.
        Sesion s = c.getSesion();
        if (s != null) {
            dto.setSesionId(s.getId());
            dto.setNumeroSesion(s.getNumero());

            Tratamiento t = s.getTratamiento();
            if (t != null) {
                dto.setTotalSesiones(t.getTotalSesiones());
                dto.setObservacion(t.getNotas());
                dto.setTratamientoId(t.getId());
                dto.setTratamientoNombre(t.getNombre());
            }
        }

        return dto;
    }
}
