package com.therateam.therateam.service;

import com.therateam.therateam.dto.CitaConPacienteRequest;
import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.dto.CitaRapidaRequest;
import com.therateam.therateam.dto.LoteResumenDTO;
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
    private final AtencionClinicaRepository atencionClinicaRepository;
    private final AtencionMetricaRepository atencionMetricaRepository;
    private final CatMetodoPagoRepository catMetodoPagoRepository;
    private final DisponibilidadService disponibilidadService;
    private final PagoService pagoService;
    private final SaldoMovimientoService saldoMovimientoService;
    private final com.therateam.therateam.repository.CitaHistorialRepository citaHistorialRepository;

    /** Claves reales de "cancelada" en el catálogo — no existe una única key "CANCELADA". */
    private static final List<String> ESTADOS_CANCELADOS = List.of("CANCELADA_PACIENTE", "CANCELADA_CLINICA");

    public List<CitaDTO> findAll() {
        return citaRepository.findAllProjected(org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    /** `terapeutaIdRestriccion` acota los resultados a un solo terapeuta (citasSoloPropias=true); null = sin restricción. */
    public Page<CitaDTO> findAllPaged(Pageable pageable, Long terapeutaIdRestriccion) {
        if (terapeutaIdRestriccion == null) return citaRepository.findAllProjected(pageable);
        return citaRepository.findByFiltrosProjected(null, null, null, terapeutaIdRestriccion, null, null, null, pageable);
    }

    public Page<CitaDTO> findByFiltrosPaged(LocalDateTime fechaInicio, LocalDateTime fechaFin, String terapeuta,
                                             Long terapeutaIdRestriccion, String estadoKey, String paciente,
                                             Long areaId, Pageable pageable) {
        String terapeutaFiltro = (terapeuta == null || terapeuta.isBlank()) ? null : terapeuta.toLowerCase();
        String pacienteFiltro = (paciente == null || paciente.isBlank()) ? null : paciente.toLowerCase();
        return citaRepository.findByFiltrosProjected(fechaInicio, fechaFin, terapeutaFiltro, terapeutaIdRestriccion,
                estadoKey, pacienteFiltro, areaId, traducirOrden(pageable));
    }

    /**
     * El front pide ordenar por "metodoPago", que no es un campo de Cita sino del ultimo Pago:
     * se traduce al alias del JOIN de la consulta (mUlt). El resto de ordenes son rutas reales
     * de la entidad y pasan tal cual.
     */
    private org.springframework.data.domain.Pageable traducirOrden(org.springframework.data.domain.Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) return pageable;
        org.springframework.data.domain.Sort traducido = org.springframework.data.domain.Sort.by(
                pageable.getSort().stream()
                        .map(o -> "metodoPago".equals(o.getProperty()) ? new org.springframework.data.domain.Sort.Order(o.getDirection(), "mUlt.nombre") : o)
                        .toList());
        return org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), traducido);
    }

    public List<CitaDTO> findByFiltros(LocalDateTime fechaInicio, LocalDateTime fechaFin, String terapeuta) {
        String terapeutaFiltro = (terapeuta == null || terapeuta.isBlank()) ? null : terapeuta.toLowerCase();
        return citaRepository.findByFiltrosProjected(fechaInicio, fechaFin, terapeutaFiltro, null, null, null, null,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    public Optional<CitaDTO> findById(Long id) {
        return citaRepository.findByIdProjected(id);
    }

    public List<CitaDTO> findByPaciente(Long pacienteId) {
        return citaRepository.findByPacienteIdProjected(pacienteId);
    }

    /** Cuántas citas de un lote de "citas masivas" faltan/ya se atendieron/se cancelaron/faltan por crear. */
    public LoteResumenDTO resumenLote(String loteMasivoId) {
        List<Cita> citas = citaRepository.findByLoteMasivoIdAndEliminadoFalse(loteMasivoId);
        int total = citas.size();
        int atendidas = (int) citas.stream()
                .filter(c -> c.getEstado() != null && "ASISTIDA".equals(c.getEstado().getKey())).count();
        int canceladas = (int) citas.stream()
                .filter(c -> c.getEstado() != null && ESTADOS_CANCELADOS.contains(c.getEstado().getKey())).count();
        int pendientes = total - atendidas - canceladas;
        Integer totalPlaneado = citas.stream().map(Cita::getLoteTotalPlaneado)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        Integer faltanPorCrear = totalPlaneado != null ? Math.max(0, totalPlaneado - (total - canceladas)) : null;
        return new LoteResumenDTO(loteMasivoId, total, atendidas, pendientes, canceladas, totalPlaneado, faltanPorCrear);
    }

    /** No se puede pasar del total planeado del lote — las citas ya canceladas no cuentan
     *  (cancelar libera cupo para crear una de reemplazo, igual que un paquete). */
    private void validarCupoLote(String loteMasivoId, Integer totalPlaneado) {
        if (totalPlaneado == null) return;
        long activas = citaRepository.findByLoteMasivoIdAndEliminadoFalse(loteMasivoId).stream()
                .filter(c -> c.getEstado() == null || !ESTADOS_CANCELADOS.contains(c.getEstado().getKey()))
                .count();
        if (activas >= totalPlaneado) {
            throw new IllegalArgumentException("Este grupo ya tiene todas sus citas creadas (" + activas + "/" + totalPlaneado + ").");
        }
    }

    /**
     * Corrección administrativa de una cita ya atendida. La edición normal prohíbe cambiar el
     * terapeuta, el tipo de terapia o el horario de una cita ASISTIDA, y esa regla se mantiene:
     * esto es la excepción explícita para quien tiene rol ADMIN, pensada para arreglar cargas
     * mal hechas, no para reescribir la agenda.
     *
     * No se revalida la disponibilidad del terapeuta: la sesión ya ocurrió, chequear cupos y
     * solapes contra un horario pasado bloquearía correcciones legítimas sin proteger nada.
     *
     * Todo lo que cambia queda en cita_historial con el motivo.
     */
    @Transactional
    public Optional<CitaDTO> corregirAtencion(Long id, com.therateam.therateam.dto.CorreccionAtencionRequest req) {
        return citaRepository.findById(id).map(cita -> {
            String estadoKey = cita.getEstado() != null ? cita.getEstado().getKey() : null;
            if (!"ASISTIDA".equals(estadoKey)) {
                throw new IllegalArgumentException(
                        "Esta corrección es solo para citas ya atendidas. Para el resto, usa la edición normal de la cita.");
            }

            List<String> cambios = new java.util.ArrayList<>();

            if (req.getTerapeutaId() != null
                    && (cita.getTerapeuta() == null || !req.getTerapeutaId().equals(cita.getTerapeuta().getId()))) {
                Terapeuta nuevo = terapeutaRepository.findById(req.getTerapeutaId())
                        .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado: " + req.getTerapeutaId()));
                cambios.add("terapeuta: " + nombreTerapeuta(cita.getTerapeuta()) + " -> " + nombreTerapeuta(nuevo));
                cita.setTerapeuta(nuevo);
            }

            if (req.getTipoTerapiaKey() != null && !req.getTipoTerapiaKey().isBlank()) {
                TipoTerapia nuevo = tipoTerapiaRepository.findByKey(req.getTipoTerapiaKey())
                        .orElseThrow(() -> new IllegalArgumentException("Tipo de terapia no encontrado: " + req.getTipoTerapiaKey()));
                String actual = cita.getTipoTerapia() != null ? cita.getTipoTerapia().getKey() : null;
                if (!nuevo.getKey().equals(actual)) {
                    cambios.add("tipo de terapia: " + (actual != null ? actual : "-") + " -> " + nuevo.getKey());
                    cita.setTipoTerapia(nuevo);
                }
            }

            if (req.getPrecio() != null && req.getPrecio().compareTo(cita.getPrecio() != null ? cita.getPrecio() : BigDecimal.ZERO) != 0) {
                cambios.add("precio: " + cita.getPrecio() + " -> " + req.getPrecio());
                cita.setPrecio(req.getPrecio());
                // El estado de pago se deriva del precio: si cambia el precio y no se recalcula,
                // una cita cobrada entera puede quedar figurando como PAGADA debiendo plata.
                BigDecimal pagado = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
                String keyPago = pagado.compareTo(req.getPrecio()) >= 0 ? "PAGADA"
                        : (pagado.compareTo(BigDecimal.ZERO) > 0 ? "PARCIAL" : "SIN_PAGO");
                catEstadoPagoCitaRepository.findByKey(keyPago).ifPresent(cita::setEstadoPago);
            }

            if (req.getMetodoPagoId() != null) {
                Pago ultimo = pagoRepository.findByCitaId(cita.getId()).stream()
                        .filter(pg -> !Boolean.TRUE.equals(pg.getEsDevolucion()) && !Boolean.TRUE.equals(pg.getEsAdicional()))
                        .max(java.util.Comparator.comparing(Pago::getId))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Esta cita no tiene ningún pago registrado, así que no hay método de pago que corregir."));
                CatMetodoPago metodo = catMetodoPagoRepository.findById(req.getMetodoPagoId())
                        .orElseThrow(() -> new IllegalArgumentException("Método de pago no encontrado: " + req.getMetodoPagoId()));
                String actual = ultimo.getMetodo() != null ? ultimo.getMetodo().getNombre() : "-";
                if (!metodo.getId().equals(ultimo.getMetodo() != null ? ultimo.getMetodo().getId() : null)) {
                    cambios.add("método de pago: " + actual + " -> " + metodo.getNombre());
                    ultimo.setMetodo(metodo);
                    pagoRepository.save(ultimo);
                }
            }

            if (cambios.isEmpty()) return toDTO(cita);

            Cita guardada = citaRepository.save(cita);
            registrarCorreccion(guardada, cambios, req.getMotivo());
            return toDTO(guardada);
        });
    }

    private String nombreTerapeuta(Terapeuta t) {
        if (t == null || t.getUsuario() == null) return "-";
        return t.getUsuario().getNombre() + " " + t.getUsuario().getApellido();
    }

    /** Deja la corrección en el historial de la cita — el estado no cambia, cambia el contenido. */
    private void registrarCorreccion(Cita cita, List<String> cambios, String motivo) {
        CitaHistorial h = new CitaHistorial();
        h.setCita(cita);
        h.setEstadoAnterior(cita.getEstado());
        h.setEstadoNuevo(cita.getEstado());
        h.setFechaAnterior(cita.getFechaInicio());
        h.setFechaNueva(cita.getFechaInicio());
        h.setCanal("CORRECCION");
        String detalle = "Corrección de atención — " + String.join("; ", cambios);
        if (motivo != null && !motivo.isBlank()) detalle += " · Motivo: " + motivo.trim();
        h.setMotivo(detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
        citaHistorialRepository.save(h);
    }

    public Cita save(Cita cita) { return citaRepository.save(cita); }

    /** Transaccional porque al salir de ASISTIDA se borra la atención y se reajusta el paquete:
     *  o pasa todo, o no pasa nada. */
    @Transactional
    public Optional<Cita> update(Long id, Cita data) {
        return citaRepository.findById(id).map(e -> {
            Long terapeutaOriginalId = e.getTerapeuta() != null ? e.getTerapeuta().getId() : null;
            LocalDateTime inicioOriginal = e.getFechaInicio();
            LocalDateTime finOriginal = e.getFechaFin();
            String estadoOriginalKey = e.getEstado() != null ? e.getEstado().getKey() : null;

            if (data.getPaciente() != null) e.setPaciente(data.getPaciente());
            e.setTerapeuta(data.getTerapeuta());
            e.setModalidad(data.getModalidad());
            // Reprogramar (cambiar fecha/hora) una cita ya atendida no tiene sentido — la atención
            // clínica ya quedó registrada contra ese horario. Sí se permite reprogramar citas
            // pagadas o parcialmente pagadas mientras no se hayan atendido todavía.
            boolean fechaCambio = !java.util.Objects.equals(inicioOriginal, data.getFechaInicio())
                    || !java.util.Objects.equals(finOriginal, data.getFechaFin());
            if (fechaCambio && "ASISTIDA".equals(estadoOriginalKey)) {
                throw new IllegalArgumentException("No se puede reprogramar una cita que ya fue atendida.");
            }

            e.setFechaInicio(data.getFechaInicio());
            e.setFechaFin(data.getFechaFin());
            e.setDuracionMinutos(data.getDuracionMinutos());

            // Cambiar el tipo de terapia solo mientras no haya atencion registrada: una vez
            // atendida, la nota clinica quedo asociada a ese tipo. Antes ni siquiera se aplicaba,
            // asi que la edicion respondia OK y el cambio se perdia.
            if (data.getTipoTerapiaKey() != null && !data.getTipoTerapiaKey().isBlank()) {
                if ("ASISTIDA".equals(estadoOriginalKey)) {
                    throw new IllegalArgumentException(
                            "No se puede cambiar el tipo de terapia de una cita ya atendida.");
                }
                tipoTerapiaRepository.findByKey(data.getTipoTerapiaKey()).ifPresent(e::setTipoTerapia);
            }
            // Salir de ASISTIDA deshace lo que hizo el registro de la atención: si no, la atención
            // quedaba huérfana (visible en el perfil del paciente) y la sesión seguía contando como
            // atendida en su paquete.
            String estadoNuevoKey = data.getEstado() != null ? data.getEstado().getKey() : null;
            if ("ASISTIDA".equals(estadoOriginalKey) && !"ASISTIDA".equals(estadoNuevoKey)) {
                revertirAtencion(e);
            }
            e.setEstado(data.getEstado());
            e.setLinkVideollamada(data.getLinkVideollamada());
            e.setNotasPrevias(data.getNotasPrevias());
            e.setRecordatorioEnviado(data.getRecordatorioEnviado());
            if (data.getTipoRecurrencia() != null) e.setTipoRecurrencia(data.getTipoRecurrencia());
            // Solo el frontend manda este campo cuando el usuario logueado es ADMIN (gate en el
            // modal de edición) — si no viene, se preserva el precio ya guardado.
            if (data.getPrecio() != null) e.setPrecio(data.getPrecio());

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

            // No se puede marcar una cita como atendida sin ningún pago registrado — evita que
            // quede "Asistida" una sesión que nunca se cobró, lo que rompería la cobranza del
            // paquete o de la cita suelta. Ojo: `e.getEstado()` acá puede ser un objeto "cascarón"
            // (Jackson solo deserializó el id desde `{"estado":{"id":X}}`), así que su key real se
            // resuelve por id en vez de confiar en getKey() directo, que vendría null.
            if (e.getEstado() != null && e.getEstado().getId() != null) {
                String estadoKey = catEstadoCitaRepository.findById(e.getEstado().getId())
                        .map(CatEstadoCita::getKey).orElse(null);
                if ("ASISTIDA".equals(estadoKey)) {
                    String estadoPagoKey = e.getEstadoPago() != null ? e.getEstadoPago().getKey() : null;
                    if (estadoPagoKey == null || "SIN_PAGO".equals(estadoPagoKey)) {
                        throw new IllegalArgumentException(
                                "No se puede marcar la cita como Asistida sin un pago registrado — registra al menos un abono primero.");
                    }
                }
            }

            return citaRepository.save(e);
        });
    }

    /**
     * Eliminación lógica: marca la cita como eliminada en vez de borrar la fila. El pago, el
     * historial y la atención clínica quedan intactos (dinero y auditoría no deben desaparecer
     * solo porque se borró la cita) — desaparece de agendas y listados porque esas consultas ya
     * filtran `eliminado = false`. Si la cita era de un paquete, se libera la sesión para que se
     * pueda volver a programar.
     */
    @Transactional
    public boolean delete(Long id) {
        return citaRepository.findById(id).map(c -> {
            c.setEliminado(true);
            citaRepository.save(c);
            sesionRepository.desvincularCitaActiva(id);
            return true;
        }).orElse(false);
    }

    /**
     * Anula la cita (queda en un estado CANCELADA_*, visible en agendas como cancelada, y libera
     * el horario/sesión) y resuelve el dinero según `tipoDevolucion`:
     * - "SALDO" (default): el monto ya aplicado se retira de la deuda pero no desaparece, pasa a
     *   quedar como saldo a favor del paciente para su próxima cita/paquete.
     * - "DINERO": se revierte la deuda y se registra una devolución (auditable, el pago original
     *   nunca se borra); no genera saldo a favor.
     * Si la cita es de un paquete, el dinero se descuenta del `totalCobrado` del tratamiento (no
     * hay un Pago 1:1 con la sesión — puede estar repartido entre varios). `metodoId` es opcional:
     * solo se usa para el registro de devolución de una cita de paquete cuando no se puede inferir
     * del historial de pagos del tratamiento.
     * No se puede anular una cita ya ASISTIDA (para eso hay que deshacer la atención primero).
     */
    @Transactional
    public Optional<CitaDTO> anular(Long id, String tipoDevolucion, Long metodoId) {
        return citaRepository.findById(id).map(cita -> {
            validarAnulable(cita);
            anularCitaInterna(cita, tipoDevolucion, metodoId);
            Cita saved = citaRepository.save(cita);
            sesionRepository.desvincularCitaActiva(id);
            return toDTO(saved);
        });
    }

    /**
     * Anula TODAS las citas pendientes (no ASISTIDA, no ya canceladas) de un paquete, con el mismo
     * criterio de devolución que {@link #anular}. Las sesiones ya atendidas no se tocan.
     */
    @Transactional
    public List<CitaDTO> anularPaquete(Long tratamientoId, String tipoDevolucion, Long metodoId) {
        if (!tratamientoRepository.existsById(tratamientoId)) {
            throw new IllegalArgumentException("Paquete no encontrado: " + tratamientoId);
        }
        List<CitaDTO> resultado = new java.util.ArrayList<>();
        for (Sesion s : sesionRepository.findByTratamientoIdWithCita(tratamientoId)) {
            Cita cita = s.getCitaActiva();
            if (cita == null) continue;
            String estadoActual = cita.getEstado() != null ? cita.getEstado().getKey() : null;
            if ("ASISTIDA".equals(estadoActual) || ESTADOS_CANCELADOS.contains(estadoActual)) continue;

            anularCitaInterna(cita, tipoDevolucion, metodoId);
            Cita saved = citaRepository.save(cita);
            sesionRepository.desvincularCitaActiva(cita.getId());
            resultado.add(toDTO(saved));
        }
        return resultado;
    }

    private void validarAnulable(Cita cita) {
        String estadoActual = cita.getEstado() != null ? cita.getEstado().getKey() : null;
        if ("ASISTIDA".equals(estadoActual)) {
            throw new IllegalArgumentException("No se puede anular una cita que ya fue atendida.");
        }
        if (ESTADOS_CANCELADOS.contains(estadoActual)) {
            throw new IllegalArgumentException("Esta cita ya está anulada.");
        }
    }

    /** Revierte el dinero de una cita (suelta o de paquete) y la deja CANCELADA_CLINICA. */
    private void anularCitaInterna(Cita cita, String tipoDevolucion, Long metodoId) {
        boolean devolverComoDinero = "DINERO".equalsIgnoreCase(tipoDevolucion);

        if (cita.getSesion() != null && cita.getSesion().getTratamiento() != null) {
            // Cita de paquete: el pago está contra el tratamiento (repartido entre sesiones), no
            // hay un Pago propio de esta cita — se revierte por monto, no por fila de Pago.
            Tratamiento tratamiento = cita.getSesion().getTratamiento();
            BigDecimal montoDeEstaSesion = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
            if (montoDeEstaSesion.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalCobrado = tratamiento.getTotalCobrado() != null ? tratamiento.getTotalCobrado() : BigDecimal.ZERO;
                tratamiento.setTotalCobrado(totalCobrado.subtract(montoDeEstaSesion).max(BigDecimal.ZERO));
                tratamientoRepository.save(tratamiento);

                if (devolverComoDinero) {
                    Long metodoResuelto = metodoId != null ? metodoId : pagoService.metodoMasRecienteDelTratamiento(tratamiento.getId());
                    String numeroSesion = cita.getSesion().getNumero() != null ? " #" + cita.getSesion().getNumero() : "";
                    pagoService.crearDevolucionManual(cita.getPaciente(), tratamiento, cita, montoDeEstaSesion, metodoResuelto,
                            "Devolución por anulación de sesión" + numeroSesion + " del paquete " + tratamiento.getNombre());
                } else {
                    String numeroSesion = cita.getSesion().getNumero() != null ? " #" + cita.getSesion().getNumero() : "";
                    sumarSaldoAFavor(cita.getPaciente(), montoDeEstaSesion,
                            "Anulación de sesión" + numeroSesion + " del paquete " + tratamiento.getNombre(), cita);
                }
            }
            cita.setMontoPagado(BigDecimal.ZERO);
            cita.setEstadoPago(catEstadoPagoCitaRepository.findByKey("SIN_PAGO").orElse(null));
        } else {
            // Cita suelta: usa los Pagos ligados directamente a ella.
            for (Pago pago : pagoRepository.findByCitaId(cita.getId())) {
                // Los pagos ya son devoluciones o cobros adicionales — no se vuelven a procesar.
                if (Boolean.TRUE.equals(pago.getEsDevolucion()) || Boolean.TRUE.equals(pago.getEsAdicional())) continue;
                if (devolverComoDinero) {
                    pagoService.devolver(pago.getId());
                } else {
                    pagoService.revertirComoSaldoAFavor(pago.getId());
                }
            }
        }

        CatEstadoCita cancelada = catEstadoCitaRepository.findByKey("CANCELADA_CLINICA")
                .orElseThrow(() -> new IllegalStateException("No existe el estado CANCELADA_CLINICA en el catálogo."));
        cita.setEstado(cancelada);
    }

    private void sumarSaldoAFavor(Paciente paciente, BigDecimal monto) {
        sumarSaldoAFavor(paciente, monto, "Anulación de cita", null);
    }

    private void sumarSaldoAFavor(Paciente paciente, BigDecimal monto, String motivo, Cita citaOrigen) {
        if (paciente == null || paciente.getId() == null || monto.compareTo(BigDecimal.ZERO) <= 0) return;
        pacienteRepository.findById(paciente.getId()).ifPresent(p -> {
            BigDecimal saldo = p.getSaldoAFavor() != null ? p.getSaldoAFavor() : BigDecimal.ZERO;
            BigDecimal nuevo = saldo.add(monto);
            p.setSaldoAFavor(nuevo);
            pacienteRepository.save(p);
            saldoMovimientoService.registrar(p, monto, nuevo, motivo, citaOrigen, null);
        });
    }

    /**
     * Da la sesión donde debe engancharse una cita nueva de este paquete. Si una sesión anterior
     * quedó libre (su cita fue anulada — {@link #anularCitaInterna} desvincula pero no borra la
     * Sesion), se reutiliza esa en vez de crear una nueva; así anular de verdad libera cupo para
     * reprogramar. Solo se crea una Sesion nueva si no hay ninguna libre Y el paquete todavía no
     * llegó a su total de sesiones contratadas — si no, se rechaza (evita que un paquete de 5
     * termine con 6+ sesiones creadas).
     */
    private Sesion obtenerOCrearSesionDisponible(Tratamiento tratamiento) {
        List<Sesion> sesiones = sesionRepository.findByTratamientoId(tratamiento.getId());

        Optional<Sesion> libre = sesiones.stream()
                .filter(s -> s.getCitaActiva() == null)
                .min(java.util.Comparator.comparing(Sesion::getNumero));
        if (libre.isPresent()) return libre.get();

        int totalSesiones = tratamiento.getTotalSesiones() != null ? tratamiento.getTotalSesiones() : 0;
        if (sesiones.size() >= totalSesiones) {
            throw new IllegalArgumentException("El paquete ya tiene todas sus sesiones creadas ("
                    + sesiones.size() + "/" + totalSesiones + ").");
        }

        CatEstadoSesion estadoSesion = estadoSesionInicial();
        Sesion nueva = new Sesion();
        nueva.setTratamiento(tratamiento);
        nueva.setNumero(sesiones.size() + 1);
        nueva.setEstado(estadoSesion);
        return sesionRepository.save(nueva);
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

            sesion = obtenerOCrearSesionDisponible(tratamiento);
            siguienteNumero = sesion.getNumero();
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

    /**
     * Deshace el registro de la atención de una cita que deja de estar ASISTIDA: borra la atención,
     * devuelve su sesión a PENDIENTE y descuenta la sesión atendida del paquete. Es la operación
     * inversa exacta de AtencionClinicaService.registrar().
     */
    /**
     * Estado con el que nace una sesión y al que vuelve si se deshace su atención. El catálogo real
     * usa "PENDIENTE_AGENDAR" (no existe ninguna key "PENDIENTE"), y el fallback cubre instalaciones
     * con otro catálogo cargado.
     */
    private CatEstadoSesion estadoSesionInicial() {
        return catEstadoSesionRepository.findByKey("PENDIENTE_AGENDAR")
                .or(() -> catEstadoSesionRepository.findByKey("PENDIENTE"))
                .orElseGet(() -> catEstadoSesionRepository.findAll().stream().findFirst().orElse(null));
    }

    private void revertirAtencion(Cita cita) {
        atencionClinicaRepository.findByCitaId(cita.getId()).ifPresent(atencion -> {
            // Las métricas no tienen cascade desde AtencionClinica, así que hay que borrarlas
            // primero o el flush falla por FK huérfana — mismo orden que usa registrar().
            atencionMetricaRepository.deleteByAtencionId(atencion.getId());
            atencionClinicaRepository.delete(atencion);
        });

        Sesion sesion = cita.getSesion();
        if (sesion == null) return;

        CatEstadoSesion estadoInicial = estadoSesionInicial();
        if (estadoInicial != null) {
            sesion.setEstado(estadoInicial);
            sesionRepository.save(sesion);
        }

        Tratamiento tratamiento = sesion.getTratamiento();
        if (tratamiento != null) {
            int actual = tratamiento.getSesionesAtendidas() != null ? tratamiento.getSesionesAtendidas() : 0;
            // Nunca por debajo de 0: si el contador ya estaba descuadrado, restar a ciegas lo
            // dejaría en negativo y el paquete mostraría sesiones imposibles.
            tratamiento.setSesionesAtendidas(Math.max(0, actual - 1));
            tratamientoRepository.save(tratamiento);
        }
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
        // El alta rapida desde Citas tambien recoge fecha de nacimiento y apoderado: sin esto,
        // registrar un menor desde la agenda saltaba la validacion que si aplica en Pacientes.
        nuevo.setFechaNacimiento(input.getFechaNacimiento());
        nuevo.setDniApoderado(input.getDniApoderado());
        nuevo.setNombreApoderado(input.getNombreApoderado());
        nuevo.setCelularApoderado(input.getCelularApoderado());
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
                    req.getPrecioPorSesion(), req.getTratamientoId(), tipoRecurrencia,
                    req.getLoteMasivoId(), req.getTotalSesionesPlan()));
        }

        // Acompañantes: paciente2 (compatibilidad) mas la lista, que es lo que usa el front
        // para los tipos con cupo mayor a dos. Cada uno genera su propia cita en el mismo
        // horario; validarDisponibilidad se encarga de no pasarse del maxPacientes del tipo.
        List<PacienteInput> acompanantes = new java.util.ArrayList<>();
        if (req.getPaciente2() != null) acompanantes.add(req.getPaciente2());
        if (req.getPacientesAdicionales() != null) acompanantes.addAll(req.getPacientesAdicionales());

        for (PacienteInput input : acompanantes) {
            if (input == null) continue;
            Paciente acompanante = buscarOCrearPaciente(input);
            if (acompanante == null) continue;
            resultado.add(crearCitaParaPaciente(acompanante, terapeuta, tipoTerapia, estadoCita, modalidad,
                    req.getFechaInicio(), fechaFin, req.getDuracionMinutos(), req.getObservacion(),
                    req.getPrecioPorSesion(), null, tipoRecurrencia, null, null));
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
                                           String tipoRecurrencia,
                                           String loteMasivoId,
                                           Integer loteTotalPlaneado) {
        validarDisponibilidad(terapeuta, fechaInicio, fechaFin,
                tipoTerapia != null ? tipoTerapia.getMaxPacientes() : null, null);
        validarPacienteDisponible(paciente, fechaInicio, fechaFin, null);

        if (loteMasivoId != null) {
            validarCupoLote(loteMasivoId, loteTotalPlaneado);
        }

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
            sesion = obtenerOCrearSesionDisponible(tratamiento);
            siguienteNumero = sesion.getNumero();
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
        cita.setLoteMasivoId(loteMasivoId);
        cita.setLoteTotalPlaneado(loteTotalPlaneado);
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
                ? citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndIdNotAndEliminadoFalse(
                        terapeuta.getId(), fin, inicio, ESTADOS_CANCELADOS, excluirCitaId)
                : citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
                        terapeuta.getId(), fin, inicio, ESTADOS_CANCELADOS);
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
                ? citaRepository.findByPacienteIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndIdNotAndEliminadoFalse(
                        paciente.getId(), fin, inicio, ESTADOS_CANCELADOS, excluirCitaId)
                : citaRepository.findByPacienteIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
                        paciente.getId(), fin, inicio, ESTADOS_CANCELADOS);
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
        dto.setLoteMasivoId(c.getLoteMasivoId());

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
