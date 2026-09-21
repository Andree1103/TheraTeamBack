package com.therateam.therateam.service;

import com.therateam.therateam.dto.AtencionClinicaRequest;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AtencionClinicaService {

    private final AtencionClinicaRepository repository;
    private final AtencionMetricaRepository metricaRepository;
    private final CitaRepository citaRepository;
    private final SesionRepository sesionRepository;
    private final TratamientoRepository tratamientoRepository;
    private final CatEstadoSesionRepository catEstadoSesionRepository;
    private final CatEstadoCitaRepository catEstadoCitaRepository;
    /** Para devolver el dinero de una inasistencia por el mismo camino que lo hace anular. */
    private final CitaService citaService;
    private final CitaHistorialRepository citaHistorialRepository;

    public List<AtencionClinica> findAll() { return repository.findAll(); }

    public Optional<AtencionClinica> findById(Long id) { return repository.findById(id); }

    public Optional<AtencionClinica> findByCita(Long citaId) {
        return repository.findByCitaId(citaId);
    }

    /** Todas las atenciones del paciente — lo que el perfil necesita para su pestaña. */
    public List<AtencionClinica> findByPaciente(Long pacienteId) {
        return repository.findByPacienteId(pacienteId);
    }

    /**
     * Registra o actualiza la atención de una cita (upsert por cita_id):
     *   - Si es nueva: guarda atencion + métricas, actualiza sesion→ATENDIDA,
     *     tratamiento.sesionesAtendidas++, cita→ASISTIDA.
     *   - Si ya existe: actualiza atencion + reemplaza métricas.
     *     No vuelve a incrementar sesionesAtendidas.
     */
    @Transactional
    public AtencionClinica registrar(AtencionClinicaRequest req) {
        Cita cita = citaRepository.findById(req.getCitaId())
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + req.getCitaId()));

        if (cita.getEstadoPago() == null || !"PAGADA".equals(cita.getEstadoPago().getKey())) {
            throw new IllegalArgumentException(
                    "No se puede registrar atención: la cita no está pagada por completo.");
        }

        boolean esNueva = false;
        AtencionClinica atencion = repository.findByCitaId(req.getCitaId()).orElse(null);

        if (atencion == null) {
            atencion = new AtencionClinica();
            atencion.setCita(cita);
            esNueva = true;
        }

        atencion.setTipo("ATENDIDA");
        atencion.setMotivo(null);
        atencion.setFechaInicioReal(req.getFechaInicioReal());
        atencion.setNotasPost(req.getNotasPost());
        atencion = repository.save(atencion);

        // Reemplazar métricas
        metricaRepository.deleteByAtencionId(atencion.getId());
        if (req.getMetricas() != null) {
            for (AtencionClinicaRequest.MetricaInput mi : req.getMetricas()) {
                AtencionMetrica m = new AtencionMetrica();
                m.setAtencion(atencion);
                m.setMetrica(mi.getMetrica());
                m.setValor(mi.getValor());
                m.setUnidad(mi.getUnidad());
                metricaRepository.save(m);
            }
        }

        // Solo primera vez: actualizar estados y contador
        if (esNueva) {
            Sesion sesion = cita.getSesion();
            if (sesion != null) {
                catEstadoSesionRepository.findByKey("ATENDIDA").ifPresent(est -> {
                    sesion.setEstado(est);
                    sesionRepository.save(sesion);
                });

                Tratamiento tratamiento = sesion.getTratamiento();
                if (tratamiento != null) {
                    int actual = tratamiento.getSesionesAtendidas() != null ? tratamiento.getSesionesAtendidas() : 0;
                    tratamiento.setSesionesAtendidas(actual + 1);
                    tratamientoRepository.save(tratamiento);
                }
            }
        }

        // El estado de la cita queda AMARRADO a la atencion: mientras exista una atencion
        // registrada la cita es ASISTIDA. Antes esto solo se hacia la primera vez, asi que una
        // cita que despues quedaba en otro estado seguia teniendo su atencion y ya no volvia a
        // sincronizarse al editarla.
        if (cita.getEstado() == null || !"ASISTIDA".equals(cita.getEstado().getKey())) {
            catEstadoCitaRepository.findByKey("ASISTIDA").ifPresent(est -> {
                cita.setEstado(est);
                citaRepository.save(cita);
            });
        }

        return atencion;
    }

    /**
     * El paciente no vino: queda registrado como una atencion de tipo INASISTENCIA, con su motivo.
     *
     * POR QUE PASA POR AQUI Y NO POR EL ESTADO DE LA CITA: "No asistio" en la agenda vive en una
     * semana que nadie vuelve a mirar. Como fila de Atenciones se puede contar, filtrar y exportar
     * junto a las sesiones que si se dieron, que es lo que hace falta para hablar con el paciente.
     *
     * NO CUENTA COMO SESION ATENDIDA: no toca sesiones_atendidas ni marca la sesion ATENDIDA. Si
     * la clinica decide cobrar la inasistencia, eso se resuelve por el lado del pago.
     *
     * Tampoco exige que la cita este pagada, a diferencia de registrar(): quien no vino
     * normalmente tampoco pago, y esa es justo la situacion que hay que poder anotar.
     */
    @Transactional
    public AtencionClinica registrarInasistencia(Long citaId, String motivo, LocalDateTime fecha, boolean devolver) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Indica el motivo de la inasistencia.");
        }
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + citaId));

        String estadoActual = cita.getEstado() != null ? cita.getEstado().getKey() : null;
        if ("ASISTIDA".equals(estadoActual)) {
            throw new IllegalArgumentException(
                    "Esta cita ya tiene una atencion registrada. Deshazla antes de marcar inasistencia.");
        }

        String limpioMotivo = motivo.trim();
        if (limpioMotivo.length() > 255) limpioMotivo = limpioMotivo.substring(0, 255);
        // Cuanto habia cobrado, antes de tocar nada: si se devuelve, esto es lo que vuelve.
        java.math.BigDecimal cobrado = cita.getMontoPagado() != null ? cita.getMontoPagado() : java.math.BigDecimal.ZERO;

        AtencionClinica atencion = repository.findByCitaId(citaId).orElseGet(AtencionClinica::new);
        atencion.setCita(cita);
        atencion.setTipo("INASISTENCIA");
        atencion.setMotivo(limpioMotivo);
        atencion.setConDevolucion(devolver);
        atencion.setMontoDevuelto(devolver ? cobrado : java.math.BigDecimal.ZERO);
        atencion.setFechaInicioReal(fecha != null ? fecha : cita.getFechaInicio());
        atencion.setNotasPost(null);
        atencion = repository.save(atencion);

        // Una inasistencia no tiene metricas: si la cita las tenia de un registro anterior, se van
        // con el cambio de tipo — dejarlas serian mediciones de una sesion que no ocurrio.
        metricaRepository.deleteByAtencionId(atencion.getId());

        // El motivo se copia tambien a la cita. Es el mismo campo que ya usan la anulacion y la
        // reprogramacion —"por que esta cita no se hizo como estaba"— y es lo que permite que el
        // listado de Atenciones, que se arma con citas, muestre el porque sin una consulta extra.
        // Con devolucion, el dinero vuelve al paciente como saldo a favor; sin ella se queda en la
        // clinica y la cita sigue PAGADA — es un ingreso mas, no hay nada que revertir.
        if (devolver) {
            citaService.revertirDinero(cita, false, null, "inasistencia");
        }

        cita.setMotivoEstado(limpioMotivo);
        CatEstadoCita estadoPrevio = cita.getEstado();
        CatEstadoCita noAsistio = catEstadoCitaRepository.findByKey("NO_ASISTIO").orElse(null);
        if (noAsistio != null) cita.setEstado(noAsistio);
        citaRepository.save(cita);

        // La misma bitacora que la anulacion y la reprogramacion. Sin esto, la inasistencia era
        // el unico cambio de estado que no dejaba rastro de quien lo hizo ni cuando.
        CitaHistorial h = new CitaHistorial();
        h.setCita(cita);
        h.setEstadoAnterior(estadoPrevio);
        h.setEstadoNuevo(noAsistio != null ? noAsistio : estadoPrevio);
        h.setFechaAnterior(cita.getFechaInicio());
        h.setFechaNueva(cita.getFechaInicio());
        h.setCanal("INASISTENCIA");
        String rastro = limpioMotivo + (devolver
                ? " — devuelto S/ " + cobrado + " como saldo a favor"
                : " — sin devolucion");
        h.setMotivo(rastro.length() > 500 ? rastro.substring(0, 500) : rastro);
        citaHistorialRepository.save(h);

        return atencion;
    }

    public AtencionClinica save(AtencionClinica atencion) { return repository.save(atencion); }

    public Optional<AtencionClinica> update(Long id, AtencionClinica data) {
        return repository.findById(id).map(existing -> {
            existing.setCita(data.getCita());
            existing.setFechaInicioReal(data.getFechaInicioReal());
            existing.setFechaFinReal(data.getFechaFinReal());
            existing.setDuracionRealMin(data.getDuracionRealMin());
            existing.setNotasPost(data.getNotasPost());
            existing.setArchivosUrl(data.getArchivosUrl());
            return repository.save(existing);
        });
    }

    /**
     * Borra la atención y deshace todo lo que hizo registrar(): devuelve la cita a PROGRAMADA, la
     * sesión a pendiente y descuenta la sesión atendida del paquete.
     *
     * Antes solo hacía deleteById, lo que fallaba con FK huérfana en cuanto la atención tenía
     * métricas, y en el mejor caso dejaba la cita ASISTIDA sin atención y el paquete descuadrado.
     */
    @Transactional
    public boolean delete(Long id) {
        AtencionClinica atencion = repository.findById(id).orElse(null);
        if (atencion == null) return false;

        Cita cita = atencion.getCita();

        // Las métricas no tienen cascade desde AtencionClinica: van primero o el flush falla.
        metricaRepository.deleteByAtencionId(id);
        repository.delete(atencion);

        if (cita != null) {
            catEstadoCitaRepository.findByKey("PROGRAMADA").ifPresent(est -> {
                cita.setEstado(est);
                citaRepository.save(cita);
            });

            Sesion sesion = cita.getSesion();
            if (sesion != null) {
                estadoSesionInicial().ifPresent(est -> {
                    sesion.setEstado(est);
                    sesionRepository.save(sesion);
                });

                Tratamiento tratamiento = sesion.getTratamiento();
                if (tratamiento != null) {
                    int actual = tratamiento.getSesionesAtendidas() != null ? tratamiento.getSesionesAtendidas() : 0;
                    // Con piso en 0: si el contador ya venía descuadrado, restar a ciegas lo dejaría negativo.
                    tratamiento.setSesionesAtendidas(Math.max(0, actual - 1));
                    tratamientoRepository.save(tratamiento);
                }
            }
        }
        return true;
    }

    /** El catálogo real no tiene ninguna key "PENDIENTE" — la sesión nace y vuelve a PENDIENTE_AGENDAR. */
    private Optional<CatEstadoSesion> estadoSesionInicial() {
        return catEstadoSesionRepository.findByKey("PENDIENTE_AGENDAR")
                .or(() -> catEstadoSesionRepository.findByKey("PENDIENTE"));
    }
}
