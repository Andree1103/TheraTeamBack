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
    private final CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;
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
     *   - Si ya estaba atendida: actualiza atencion + reemplaza métricas.
     *     No vuelve a incrementar sesionesAtendidas.
     *
     * "Ya estaba atendida" se mide por el tipo de la fila, no por que la fila exista: una cita
     * anotada como inasistencia o como "no se marco" ya tiene fila y sigue siendo una sesion por
     * dar. Ver el comentario dentro del metodo.
     */
    @Transactional
    public AtencionClinica registrar(AtencionClinicaRequest req) {
        Cita cita = citaRepository.findById(req.getCitaId())
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + req.getCitaId()));

        AtencionClinica atencion = repository.findByCitaId(req.getCitaId()).orElse(null);

        // "Ya tenia atencion" no es lo mismo que "ya tenia fila".
        //
        // Hay tres resultados posibles por cita y los tres viven en la misma fila (ver
        // AtencionClinica#tipo): una fila INASISTENCIA o NO_MARCO dice justamente que la sesion
        // NO se habia dado. Mirar solo si la fila existia daba por atendida una cita que no lo
        // estaba: se saltaba el cobro obligatorio y, peor, no sumaba la sesion al paquete — el
        // contador de atendidas se quedaba corto para siempre. Pasaba al atender una cita que
        // antes se habia marcado como inasistencia, y pasaria en cada cita que se anote como
        // "no se marco" y luego se atienda, que es el camino normal cuando a alguien se le paso.
        boolean esNueva = atencion == null || !"ATENDIDA".equals(atencion.getTipo());

        // Para atender, la cita tiene que estar pagada. Decision del negocio, reafirmada.
        //
        // Esta regla ya existio y se quito el 03/10, porque bloquear la pantalla cuando la
        // sesion YA OCURRIO empujaba a recepcion a inventar un pago con "Sin pago" para
        // desbloquearla: de ahi salieron un arqueo con S/ 5.290 que no estaban en el cajon y
        // saldos a favor creados de la nada al anular esas citas.
        //
        // Vuelve ahora porque ese atajo ya no hace daño: "Sin pago" esta marcado
        // cuenta_en_caja = false y no toca el arqueo, y anular una cita cobrada asi no genera
        // saldo (lo cubre el caso 6 de db/pruebas-dinero.sh). El atajo sigue estando; lo que
        // se le quito fueron las dos consecuencias.
        //
        // Solo al CREAR: si la atencion ya existe se puede corregir aunque la cita siga
        // debiendo. Si no, las sesiones que se atendieron antes de esta regla quedarian
        // congeladas, sin poder ni arreglarles una nota.
        if (esNueva) {
            String estadoPago = cita.getEstadoPago() != null ? cita.getEstadoPago().getKey() : null;
            if (!"PAGADA".equals(estadoPago)) {
                java.math.BigDecimal precio = cita.getPrecio() != null ? cita.getPrecio() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal pagado = cita.getMontoPagado() != null ? cita.getMontoPagado() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal falta = precio.subtract(pagado).max(java.math.BigDecimal.ZERO);
                throw new IllegalArgumentException(
                        "No se puede registrar la atencion: la cita no esta pagada"
                      + (falta.compareTo(java.math.BigDecimal.ZERO) > 0 ? " (faltan S/ " + falta + ")" : "")
                      + ". Cobrala primero desde Pagos o desde la propia cita.");
            }
        }

        if (atencion == null) {
            atencion = new AtencionClinica();
            atencion.setCita(cita);
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

        // Solo la primera vez que SE DA la sesion: marcarla atendida y sumarla al paquete.
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
        // Una devolucion que YA se hizo no se puede deshacer desde aqui: el dinero esta en el
        // saldo del paciente y su movimiento en el ledger. Volver a marcar la inasistencia —para
        // corregir el motivo, o por un doble clic— no debe borrar ese dato: la ficha diria "se
        // cobro igual" sobre un dinero que si volvio, y el flag existe justo para que eso no pase.
        boolean yaSeDevolvio = Boolean.TRUE.equals(atencion.getConDevolucion());
        java.math.BigDecimal devueltoAntes = atencion.getMontoDevuelto() != null
                ? atencion.getMontoDevuelto() : java.math.BigDecimal.ZERO;

        atencion.setCita(cita);
        atencion.setTipo("INASISTENCIA");
        atencion.setMotivo(limpioMotivo);
        atencion.setConDevolucion(yaSeDevolvio || devolver);
        atencion.setMontoDevuelto(yaSeDevolvio ? devueltoAntes
                                               : (devolver ? cobrado : java.math.BigDecimal.ZERO));
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
        // Solo se mueve dinero la primera vez. Sin el guard, pedir devolucion sobre una cita que
        // ya se devolvio volveria a llamar a revertirDinero; hoy no cobraria nada (la cita quedo
        // en SIN_PAGO) pero es un doble abono esperando a que cambie algo aguas abajo.
        if (devolver && !yaSeDevolvio) {
            citaService.revertirDinero(cita, false, null, "inasistencia");
        } else if (cobrado.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Sin devolucion, el dinero se queda en la clinica. Antes la cita seguia en PAGADA,
            // indistinguible de una sesion que si se dio: en la ficha se leia "No asistio /
            // Pagada", que suena a error, y en cualquier recuento el cobro por trabajo hecho y
            // el cobro por penalidad caian en la misma bolsa. DESCONTADA es lo mismo en dinero
            // —no se mueve un sol— pero dicho con su nombre.
            catEstadoPagoCitaRepository.findByKey("DESCONTADA").ifPresent(cita::setEstadoPago);
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
        String rastro = limpioMotivo + (atencion.getConDevolucion()
                ? " — devuelto S/ " + atencion.getMontoDevuelto() + " como saldo a favor"
                : " — sin devolucion");
        h.setMotivo(rastro.length() > 500 ? rastro.substring(0, 500) : rastro);
        citaHistorialRepository.save(h);

        return atencion;
    }

    /**
     * Nadie marco esta cita: queda la constancia de que se paso, firmada y con fecha.
     *
     * Es el tercer resultado, al lado de "se atendio" y "no vino". Hacia falta porque los dos
     * que habia solo cubren las citas que alguien si llego a mirar: la que se le paso a la
     * encargada no era ningun resultado, era la ausencia de uno, y una ausencia no se puede
     * revisar. En la pantalla aparecian igual la cita que nadie habia visto nunca y la que ya se
     * habia revisado sin poder reconstruir que paso, asi que cada repaso volvia a empezar por las
     * mismas filas.
     *
     * LO QUE NO HACE, que es casi todo: no mueve un sol, no cuenta como sesion dada, no suma al
     * contador del paquete y NO cambia el estado de la cita. Esto ultimo es a proposito y es la
     * diferencia con marcar inasistencia: decir "no se marco" no es decir que el paciente no
     * vino. Lo que paso sigue sin saberse — lo que se guarda es que ya se noto que no se sabe. La
     * cita se queda Programada, que es la verdad, y sigue saliendo en el filtro de sin marca
     * hasta que alguien la resuelva.
     *
     * Por eso tampoco cierra nada: desde aqui se puede marcar atendida o inasistencia como si no
     * existiera esta fila, y registrar() la sobreescribe sumando la sesion al paquete como
     * corresponde (ver el comentario de esNueva alli).
     *
     * El motivo es opcional, al contrario que en la inasistencia. Ahi el motivo es el dato — sin
     * el, la fila no dice nada que no dijera el estado. Aqui el dato es que nadie marco, y
     * exigir que ademas se explique por que no se marco condenaba la funcion a no usarse: quien
     * repasa el mes no sabe por que alguien olvido una cita en setiembre, y lo que hay que poder
     * hacer es dejarla senalada igual.
     */
    @Transactional
    public AtencionClinica registrarNoMarco(Long citaId, String motivo) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + citaId));

        String estadoActual = cita.getEstado() != null ? cita.getEstado().getKey() : null;
        if ("ASISTIDA".equals(estadoActual)) {
            throw new IllegalArgumentException(
                    "Esta cita tiene una atencion registrada: ya se sabe que paso. "
                  + "Si eso esta mal, deshaz la atencion primero.");
        }
        // Las inasistencias, anulaciones y reprogramaciones ya son una respuesta. Volver a
        // dejarlas "sin marca" borraria un dato bueno por uno peor.
        if (EstadosDeCita.noSeCobra(estadoActual)) {
            String nombre = cita.getEstado() != null && cita.getEstado().getNombre() != null
                    ? cita.getEstado().getNombre() : estadoActual;
            throw new IllegalArgumentException(
                    "Esta cita ya esta resuelta (" + nombre + "): no es una cita sin marcar.");
        }
        // Una cita que todavia no ha pasado no esta sin marcar: esta por ocurrir.
        if (cita.getFechaInicio() != null && cita.getFechaInicio().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Esta cita todavia no ha pasado: no hay nada que marcar.");
        }

        AtencionClinica atencion = repository.findByCitaId(citaId).orElseGet(AtencionClinica::new);
        // Con el estado de la cita ya filtrado arriba, una fila ATENDIDA o INASISTENCIA aqui
        // significa que la fila y la cita no coinciden. Es un dato roto, y machacarlo seria
        // perder el unico rastro de lo que si se habia anotado.
        if (atencion.getId() != null && !"NO_MARCO".equals(atencion.getTipo())) {
            throw new IllegalArgumentException(
                    "Esta cita ya tiene anotado un resultado (" + atencion.getTipo()
                  + ") que no cuadra con su estado. Revisala antes de dejarla sin marca.");
        }

        String limpio = motivo != null && !motivo.isBlank() ? motivo.trim() : null;
        if (limpio != null && limpio.length() > 255) limpio = limpio.substring(0, 255);

        atencion.setCita(cita);
        atencion.setTipo("NO_MARCO");
        atencion.setMotivo(limpio);
        atencion.setConDevolucion(false);
        atencion.setMontoDevuelto(java.math.BigDecimal.ZERO);
        // La hora a la que la cita TENIA que pasar. Aqui no hubo sesion, asi que no hay hora
        // real que anotar, pero la columna no admite nulos y la fila necesita una fecha con la
        // que ordenarse en Atenciones. Es la misma salida que toma la inasistencia, por lo mismo.
        atencion.setFechaInicioReal(cita.getFechaInicio());
        atencion = repository.save(atencion);

        // La misma bitacora que la inasistencia, la anulacion y la reprogramacion. El estado no
        // cambia, asi que anterior y nuevo son el mismo: lo que se esta guardando es quien lo
        // reviso y cuando, que es todo el valor de esta fila.
        CitaHistorial h = new CitaHistorial();
        h.setCita(cita);
        h.setEstadoAnterior(cita.getEstado());
        h.setEstadoNuevo(cita.getEstado());
        h.setFechaAnterior(cita.getFechaInicio());
        h.setFechaNueva(cita.getFechaInicio());
        h.setCanal("SIN_MARCA");
        String rastro = "Nadie marco si vino o no"
                + (limpio != null ? " — " + limpio : "");
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
     * Borra la fila de resultado y deshace lo que esa fila hizo — ni mas ni menos.
     *
     * Si era una atencion de verdad, deshace todo lo de registrar(): devuelve la cita a
     * PROGRAMADA, la sesion a pendiente y descuenta la sesion atendida del paquete. Si era una
     * inasistencia, solo devuelve la cita a PROGRAMADA (la inasistencia nunca sumo la sesion). Y
     * si era un "no se marco", no deshace nada: esa fila no habia cambiado nada, solo dejaba
     * constancia.
     *
     * Antes restaba una sesion atendida en los tres casos, asi que borrar una inasistencia
     * dejaba el contador del paquete por debajo de la realidad.
     *
     * Antes de eso solo hacía deleteById, lo que fallaba con FK huérfana en cuanto la atención
     * tenía métricas, y en el mejor caso dejaba la cita ASISTIDA sin atención y el paquete
     * descuadrado.
     */
    @Transactional
    public boolean delete(Long id) {
        AtencionClinica atencion = repository.findById(id).orElse(null);
        if (atencion == null) return false;

        Cita cita = atencion.getCita();
        // Solo hay que deshacer lo que se hizo. Esta fila puede ser una inasistencia o un "no se
        // marco", y ninguna de las dos sumo la sesion al paquete ni puso la cita en Asistida:
        // deshacerlas igual restaba una sesion atendida que nunca se conto y dejaba el contador
        // del paquete por debajo de la realidad, sin nada que lo delatara.
        boolean sumoLaSesion = "ATENDIDA".equals(atencion.getTipo());
        String estadoActual = cita != null && cita.getEstado() != null ? cita.getEstado().getKey() : null;

        // Las métricas no tienen cascade desde AtencionClinica: van primero o el flush falla.
        metricaRepository.deleteByAtencionId(id);
        repository.delete(atencion);

        if (cita != null) {
            // Borrar la constancia de que no se marco no tiene por que degradar la cita: ya
            // estaba sin marcar, y pasar una Confirmada a Programada perderia la confirmacion
            // del paciente a cambio de nada.
            if (!"PROGRAMADA".equals(estadoActual) && !"CONFIRMADA".equals(estadoActual)) {
                catEstadoCitaRepository.findByKey("PROGRAMADA").ifPresent(est -> {
                    cita.setEstado(est);
                    citaRepository.save(cita);
                });
            }

            Sesion sesion = cita.getSesion();
            if (sesion != null && sumoLaSesion) {
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
