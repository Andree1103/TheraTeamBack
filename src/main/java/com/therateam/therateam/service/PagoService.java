package com.therateam.therateam.service;

import com.therateam.therateam.dto.PagoDTO;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.model.Sesion;
import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.model.VentaItem;
import com.therateam.therateam.model.CatMetodoPago;
import com.therateam.therateam.repository.CatEstadoPagoCitaRepository;
import com.therateam.therateam.repository.CatMetodoPagoRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PacienteRepository;
import com.therateam.therateam.repository.PagoRepository;
import com.therateam.therateam.repository.SesionRepository;
import com.therateam.therateam.repository.TratamientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository repository;
    private final CitaRepository citaRepository;
    private final CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;
    private final TratamientoRepository tratamientoRepository;
    private final SesionRepository sesionRepository;
    private final PacienteRepository pacienteRepository;
    private final CatMetodoPagoRepository catMetodoPagoRepository;
    private final SaldoMovimientoService saldoMovimientoService;
    private final VentaService ventaService;

    public List<PagoDTO> findAll() { return repository.findAllProjected(); }

    @Transactional(readOnly = true)
    public Page<PagoDTO> findAllPaged(Pageable pageable, String paciente, String referencia, Long metodoId,
                                       Boolean tienePaquete, BigDecimal montoMin, BigDecimal montoMax,
                                       LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        return repository.buscarPaged(blankToNull(paciente), blankToNull(referencia), metodoId, tienePaquete,
                montoMin, montoMax, fechaInicio, fechaFin, pageable);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    public Optional<Pago> findById(Long id) { return repository.findById(id); }
    public List<PagoDTO> findByTratamiento(Long tratamientoId) { return repository.findByTratamientoIdProjected(tratamientoId); }
    public List<PagoDTO> findByPaciente(Long pacienteId) { return repository.findByPacienteIdProjected(pacienteId); }

    /**
     * El saldo a favor es del PACIENTE, no de un paquete puntual: cualquier pago (a un paquete,
     * a una cita suelta con precio propio, o un "adelanto general" sin ninguno de los dos)
     * primero descuenta el crédito que el paciente ya tenía, y si sobra dinero por encima de la
     * deuda que se estaba cubriendo, ese sobrante vuelve a quedar como su saldo a favor —
     * disponible para su próxima cita o paquete, sea cuál sea. Un pago con `montoRecibido = 0`
     * es válido: cubre la deuda usando solo ese saldo, sin dinero nuevo.
     */
    @Transactional
    public Pago save(Pago p) {
        Paciente paciente = pacienteRepository.findById(p.getPaciente().getId())
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

        // Cobro adicional (ej. "se atendió y se le vendió algo más"): es ingreso aparte, no paga
        // ninguna deuda de cita/paquete ni genera saldo a favor — se registra tal cual se cobró.
        // Es también la vía de la venta de productos: si vienen items, el monto y el concepto
        // los pone el catálogo, no lo que se haya tecleado.
        if (Boolean.TRUE.equals(p.getEsAdicional())) {
            // Antes de guardar nada: si falta stock, esto lanza y no queda un pago huérfano.
            List<VentaItem> itemsVenta = ventaService.preparar(p.getItems());

            BigDecimal monto;
            if (!itemsVenta.isEmpty()) {
                monto = ventaService.total(itemsVenta);
                if (p.getConcepto() == null || p.getConcepto().isBlank()) {
                    p.setConcepto("Venta: " + ventaService.describir(itemsVenta));
                }
            } else {
                monto = p.getMontoRecibido() != null ? p.getMontoRecibido() : BigDecimal.ZERO;
            }

            p.setMontoRecibido(monto);
            p.setMontoAplicado(monto);
            p.setSaldoGenerado(BigDecimal.ZERO);
            p.setSaldoPrevio(paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO);
            Pago guardado = repository.save(p);

            // Después del save: las líneas necesitan el id del pago para colgarse de él.
            ventaService.confirmar(guardado.getId(), itemsVenta);
            guardado.setItems(itemsVenta);
            return guardado;
        }

        BigDecimal montoRecibido  = p.getMontoRecibido() != null ? p.getMontoRecibido() : BigDecimal.ZERO;
        BigDecimal saldoPrevio    = paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO;
        BigDecimal montoDisponible = montoRecibido.add(saldoPrevio);

        Cita citaAsociada = null;
        if (p.getCita() != null && p.getCita().getId() != null) {
            citaAsociada = citaRepository.findById(p.getCita().getId()).orElse(null);
        }
        boolean citaConPrecioDeReferencia = citaAsociada != null && citaAsociada.getPrecio() != null
                && citaAsociada.getPrecio().compareTo(BigDecimal.ZERO) > 0;

        Tratamiento tratamiento = null;
        BigDecimal precioReferencia = BigDecimal.ZERO;
        BigDecimal deudaPendiente;
        // Solo la rama de adelantos (cita/paquete con deuda propia) deja el estado_pago ya
        // resuelto — un "adelanto general" sin ninguno de los dos no toca ninguna cita.
        boolean resueltoPorAdelanto = false;

        if (p.getTratamiento() != null && p.getTratamiento().getId() != null) {
            tratamiento = tratamientoRepository.findById(p.getTratamiento().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado"));
            precioReferencia = tratamiento.getPrecioPorSesion() != null ? tratamiento.getPrecioPorSesion() : BigDecimal.ZERO;
            int totalSesiones = tratamiento.getTotalSesiones() != null ? tratamiento.getTotalSesiones() : 0;
            BigDecimal montoTotal   = precioReferencia.multiply(BigDecimal.valueOf(totalSesiones));
            BigDecimal totalCobrado = tratamiento.getTotalCobrado() != null ? tratamiento.getTotalCobrado() : BigDecimal.ZERO;
            deudaPendiente = montoTotal.subtract(totalCobrado).max(BigDecimal.ZERO);
            resueltoPorAdelanto = true;
        } else if (citaConPrecioDeReferencia) {
            BigDecimal montoPagadoActual = citaAsociada.getMontoPagado() != null ? citaAsociada.getMontoPagado() : BigDecimal.ZERO;
            deudaPendiente = citaAsociada.getPrecio().subtract(montoPagadoActual).max(BigDecimal.ZERO);
            resueltoPorAdelanto = true;
        } else {
            // Ni paquete ni cita con precio propio: "adelanto general" — todo el monto (más lo
            // que ya tenía a favor) queda como saldo a favor, no hay ninguna deuda que cubrir aún.
            deudaPendiente = BigDecimal.ZERO;
        }

        BigDecimal montoAplicado = montoDisponible.min(deudaPendiente);
        BigDecimal saldoGenerado = montoDisponible.subtract(montoAplicado);

        p.setSaldoPrevio(saldoPrevio);
        p.setMontoAplicado(montoAplicado);
        p.setSaldoGenerado(saldoGenerado);

        BigDecimal saldoAnterior = paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO;
        paciente.setSaldoAFavor(saldoGenerado);
        pacienteRepository.save(paciente);

        if (tratamiento != null) {
            BigDecimal totalCobrado = tratamiento.getTotalCobrado() != null ? tratamiento.getTotalCobrado() : BigDecimal.ZERO;
            tratamiento.setTotalCobrado(totalCobrado.add(montoAplicado));
            tratamientoRepository.save(tratamiento);

            // Reparte lo efectivamente aplicado entre las sesiones del paquete, para que cada
            // cita quede reflejada como PAGADA/PARCIAL/SIN_PAGO según cuánto de su precio ya se
            // cubrió — no solo el total del paquete.
            if (citaAsociada != null) {
                // Pago dirigido a una sesión puntual (ej. "Registrar pago" con citas elegidas):
                // abona solo a esa cita — permite completar una que ya estaba PARCIAL sin volver
                // a cobrar el precio completo.
                aplicarMontoACita(citaAsociada.getId(), montoAplicado, precioReferencia);
            } else if (montoAplicado.compareTo(BigDecimal.ZERO) > 0) {
                // Pago suelto contra el paquete (ej. el adelanto inicial al crearlo): se reparte
                // en orden de sesión, completando primero las que ya tenían algo pagado.
                repartirEntreSesiones(tratamiento.getId(), montoAplicado, precioReferencia);
            }
        } else if (citaConPrecioDeReferencia) {
            // Pago (adelanto o completo) de una cita suelta con precio propio: se acumula contra
            // ese precio — puede cubrirlo de a pocos (adelantos) hasta llegar a PAGADA.
            BigDecimal montoPagadoActual = citaAsociada.getMontoPagado() != null ? citaAsociada.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal montoPagadoNuevo  = montoPagadoActual.add(montoAplicado);
            citaAsociada.setMontoPagado(montoPagadoNuevo);
            citaAsociada.setEstadoPago(catEstadoPagoCitaRepository.findByKey(
                    estadoPagoPorMonto(montoPagadoNuevo, citaAsociada.getPrecio())).orElse(null));
            citaRepository.save(citaAsociada);
        }

        Pago saved = repository.save(p);

        // Despues del save: el movimiento referencia al Pago, y antes de persistirlo seria
        // una instancia transitoria (Hibernate lo rechaza al hacer flush).
        // El terapeuta sale de la cita cargada de BD o, si el pago es contra un paquete sin
        // cita puntual, del propio paquete. saved.getCita() no sirve: es el stub del request.
        Terapeuta terapeutaDelSaldo = citaAsociada != null ? citaAsociada.getTerapeuta()
                : (tratamiento != null ? tratamiento.getTerapeuta() : null);
        // El concepto nombra la cita o el paquete: sin eso, el historial dice "se uso saldo"
        // pero no en que, que es justo lo que hay que poder responder.
        String motivoSaldo;
        if (citaAsociada == null && tratamiento == null) {
            // Adelanto a cuenta: el paciente deja dinero sin nada que cubrir todavia. No es
            // "pagar de mas", asi que decirlo asi confundia en el estado de cuenta.
            motivoSaldo = "Adelanto a cuenta"
                    + (p.getNotas() != null && !p.getNotas().isBlank() ? " — " + p.getNotas() : "");
        } else {
            String concepto = describirOrigen(citaAsociada, tratamiento);
            motivoSaldo = saldoGenerado.compareTo(saldoAnterior) > 0
                    ? "Pagó de más en " + concepto + " — el excedente queda a favor"
                    : "Saldo usado en " + concepto;
        }
        saldoMovimientoService.registrar(paciente, saldoGenerado.subtract(saldoAnterior), saldoGenerado,
                motivoSaldo, citaAsociada, saved, terapeutaDelSaldo);

        // La rama de adelantos (arriba) ya dejó el estado_pago correcto según lo cobrado; para el
        // resto de casos (cita sin precio propio, ej. legado) se conserva el comportamiento previo:
        // cualquier pago ligado a la cita la marca PAGADA de una vez.
        if (!resueltoPorAdelanto && saved.getCita() != null && saved.getCita().getId() != null) {
            citaRepository.findById(saved.getCita().getId()).ifPresent(cita ->
                catEstadoPagoCitaRepository.findByKey("PAGADA").ifPresent(pagada -> {
                    cita.setEstadoPago(pagada);
                    citaRepository.save(cita);
                })
            );
        }

        return saved;
    }

    /** Abona `monto` a una cita puntual del paquete (hasta el precio de referencia), actualizando
     *  su montoPagado/estadoPago — no la fuerza a PAGADA de una, respeta lo que ya tenía pagado. */
    private void aplicarMontoACita(Long citaId, BigDecimal monto, BigDecimal precioReferencia) {
        if (monto.compareTo(BigDecimal.ZERO) <= 0) return;
        citaRepository.findById(citaId).ifPresent(cita -> {
            BigDecimal precio = cita.getPrecio() != null && cita.getPrecio().compareTo(BigDecimal.ZERO) > 0
                    ? cita.getPrecio() : precioReferencia;
            if (precio.compareTo(BigDecimal.ZERO) <= 0) return;
            BigDecimal montoPagadoActual = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal montoPagadoNuevo = montoPagadoActual.add(monto).min(precio);
            cita.setMontoPagado(montoPagadoNuevo);
            cita.setEstadoPago(catEstadoPagoCitaRepository.findByKey(
                    estadoPagoPorMonto(montoPagadoNuevo, precio)).orElse(null));
            citaRepository.save(cita);
        });
    }

    /** Reparte `monto` entre las sesiones del paquete en orden (por número), completando primero
     *  las que ya tenían algo pagado y avanzando a las siguientes hasta que se acabe el dinero —
     *  así un adelanto de S/100 con sesiones de S/57 deja la 1ra PAGADA y la 2da PARCIAL (43/57). */
    private void repartirEntreSesiones(Long tratamientoId, BigDecimal monto, BigDecimal precio) {
        if (precio.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal restante = monto;
        for (Sesion s : sesionRepository.findByTratamientoIdWithCita(tratamientoId)) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) break;
            Cita cita = s.getCitaActiva();
            if (cita == null) continue;
            BigDecimal precioCita = cita.getPrecio() != null && cita.getPrecio().compareTo(BigDecimal.ZERO) > 0
                    ? cita.getPrecio() : precio;
            BigDecimal montoPagadoActual = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
            if (montoPagadoActual.compareTo(precioCita) >= 0) continue; // ya está completa
            BigDecimal falta = precioCita.subtract(montoPagadoActual);
            BigDecimal aAplicar = restante.min(falta);
            BigDecimal montoPagadoNuevo = montoPagadoActual.add(aAplicar);
            cita.setMontoPagado(montoPagadoNuevo);
            cita.setEstadoPago(catEstadoPagoCitaRepository.findByKey(
                    estadoPagoPorMonto(montoPagadoNuevo, precioCita)).orElse(null));
            citaRepository.save(cita);
            restante = restante.subtract(aAplicar);
        }
    }

    private String estadoPagoPorMonto(BigDecimal montoPagado, BigDecimal precio) {
        if (montoPagado.compareTo(precio) >= 0) return "PAGADA";
        if (montoPagado.compareTo(BigDecimal.ZERO) > 0) return "PARCIAL";
        return "SIN_PAGO";
    }

    /** Texto corto que identifica contra que se movio el saldo, para el historial de Adelantos. */
    private String describirOrigen(Cita cita, Tratamiento tratamiento) {
        if (cita != null) {
            String tipo = cita.getTipoTerapia() != null ? cita.getTipoTerapia().getNombre() : "cita";
            String cuando = cita.getFechaInicio() != null
                    ? cita.getFechaInicio().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "";
            return tipo + (cuando.isEmpty() ? "" : " del " + cuando);
        }
        if (tratamiento != null) {
            return "el paquete " + (tratamiento.getNombre() != null ? tratamiento.getNombre() : "#" + tratamiento.getId());
        }
        return "un adelanto sin cita asociada";
    }

    /** Revierte el efecto de un pago sobre el saldo del paciente y el tratamiento/cita (usado al eliminar). */
    private void revertir(Pago p) {
        // Si el pago era una venta, los productos volvieron al estante: el contador debe subir.
        if (p.getId() != null) ventaService.devolverStock(p.getId());
        if (p.getPaciente() != null && p.getPaciente().getId() != null) {
            pacienteRepository.findById(p.getPaciente().getId()).ifPresent(paciente -> {
                // El saldo a favor que dejó este pago se retira; el saldo previo a este pago se restaura.
                BigDecimal antes = paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO;
                BigDecimal restaurado = p.getSaldoPrevio() != null ? p.getSaldoPrevio() : BigDecimal.ZERO;
                paciente.setSaldoAFavor(restaurado);
                pacienteRepository.save(paciente);
                saldoMovimientoService.registrar(paciente, restaurado.subtract(antes), restaurado,
                        "Se eliminó el pago #" + p.getId() + " — saldo restaurado", p.getCita(), null);
            });
        }
        if (p.getTratamiento() != null && p.getTratamiento().getId() != null) {
            tratamientoRepository.findById(p.getTratamiento().getId()).ifPresent(t -> {
                BigDecimal totalCobrado = t.getTotalCobrado() != null ? t.getTotalCobrado() : BigDecimal.ZERO;
                BigDecimal montoAplicado = p.getMontoAplicado() != null ? p.getMontoAplicado() : BigDecimal.ZERO;
                t.setTotalCobrado(totalCobrado.subtract(montoAplicado).max(BigDecimal.ZERO));
                tratamientoRepository.save(t);
            });
            return;
        }
        if (p.getCita() != null && p.getCita().getId() != null) {
            citaRepository.findById(p.getCita().getId()).ifPresent(cita -> {
                if (cita.getPrecio() == null || cita.getPrecio().compareTo(BigDecimal.ZERO) <= 0) return;
                BigDecimal montoPagado  = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
                BigDecimal montoAplicado = p.getMontoAplicado() != null ? p.getMontoAplicado() : BigDecimal.ZERO;
                BigDecimal nuevo = montoPagado.subtract(montoAplicado).max(BigDecimal.ZERO);
                cita.setMontoPagado(nuevo);
                cita.setEstadoPago(catEstadoPagoCitaRepository.findByKey(
                        estadoPagoPorMonto(nuevo, cita.getPrecio())).orElse(null));
                citaRepository.save(cita);
            });
        }
    }

    /**
     * Devuelve el dinero de un pago sin borrarlo — a diferencia de {@link #delete}, el pago
     * original queda intacto como evidencia de que el dinero entró, y se crea un segundo registro
     * (esDevolucion=true, ligado por pagoOrigenId) como evidencia de que salió. Se usa al anular
     * una cita con devolución "DINERO": el paciente no se queda con saldo a favor, pero tampoco
     * desaparece el rastro contable de la transacción.
     */
    @Transactional
    public Pago devolver(Long pagoOrigenId) {
        Pago original = repository.findById(pagoOrigenId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + pagoOrigenId));

        revertir(original); // deshace el efecto en tratamiento/cita y restaura el saldo previo del paciente

        BigDecimal montoDevuelto = original.getMontoAplicado() != null ? original.getMontoAplicado() : BigDecimal.ZERO;
        BigDecimal saldoActual = BigDecimal.ZERO;
        if (original.getPaciente() != null && original.getPaciente().getId() != null) {
            saldoActual = pacienteRepository.findById(original.getPaciente().getId())
                    .map(p -> p.getSaldoAFavor() != null ? p.getSaldoAFavor() : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);
        }

        Pago devolucion = new Pago();
        devolucion.setPaciente(original.getPaciente());
        devolucion.setTratamiento(original.getTratamiento());
        devolucion.setCita(original.getCita());
        devolucion.setMetodo(original.getMetodo());
        devolucion.setMontoRecibido(montoDevuelto);
        devolucion.setMontoAplicado(BigDecimal.ZERO);
        devolucion.setSaldoGenerado(BigDecimal.ZERO);
        devolucion.setSaldoPrevio(saldoActual);
        devolucion.setEsDevolucion(true);
        devolucion.setPagoOrigenId(original.getId());
        devolucion.setConcepto("Devolución del pago #" + original.getId());
        devolucion.setNotas("Devolución de dinero por anulación de cita");
        return repository.save(devolucion);
    }

    /**
     * Igual que {@link #devolver}, pero para cuando NO existe un único Pago que "sea" el origen
     * de la plata a devolver — típicamente una sesión de un paquete, cuyo cobro está repartido
     * dentro de un Pago que cubre varias sesiones a la vez. El caller ya revirtió lo que
     * corresponde en tratamiento/cita; acá solo se deja el registro de auditoría de la devolución.
     */
    @Transactional
    public Pago crearDevolucionManual(Paciente paciente, Tratamiento tratamiento, Cita cita,
                                       BigDecimal monto, Long metodoId, String concepto) {
        CatMetodoPago metodo = metodoId != null ? catMetodoPagoRepository.findById(metodoId).orElse(null) : null;
        BigDecimal saldoActual = BigDecimal.ZERO;
        if (paciente != null && paciente.getId() != null) {
            saldoActual = pacienteRepository.findById(paciente.getId())
                    .map(p -> p.getSaldoAFavor() != null ? p.getSaldoAFavor() : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);
        }

        Pago devolucion = new Pago();
        devolucion.setPaciente(paciente);
        devolucion.setTratamiento(tratamiento);
        devolucion.setCita(cita);
        devolucion.setMetodo(metodo);
        devolucion.setMontoRecibido(monto);
        devolucion.setMontoAplicado(BigDecimal.ZERO);
        devolucion.setSaldoGenerado(BigDecimal.ZERO);
        devolucion.setSaldoPrevio(saldoActual);
        devolucion.setEsDevolucion(true);
        devolucion.setConcepto(concepto);
        devolucion.setNotas(concepto);
        return repository.save(devolucion);
    }

    /** El método más reciente usado en un pago real (no devolución/adicional) del paquete —
     *  default razonable para la devolución cuando no se especifica uno explícito. */
    public Long metodoMasRecienteDelTratamiento(Long tratamientoId) {
        return repository.findByTratamientoId(tratamientoId).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getEsDevolucion()) && !Boolean.TRUE.equals(p.getEsAdicional()))
                .filter(p -> p.getMetodo() != null)
                .max((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(p -> p.getMetodo().getId())
                .orElse(null);
    }

    public boolean delete(Long id) {
        return repository.findById(id).map(p -> {
            revertir(p);
            repository.deleteById(id);
            return true;
        }).orElse(false);
    }

    /**
     * Anula el efecto de un pago sobre la deuda del tratamiento/cita (como {@link #revertir}),
     * pero a diferencia de {@link #delete} el monto ya aplicado no desaparece: se suma al saldo
     * a favor del paciente. El registro del pago se conserva (auditoría de que el dinero entró),
     * solo deja de contar como abono de esa cita/paquete. Usado al anular una cita con
     * devolución "SALDO".
     */
    @Transactional
    public void revertirComoSaldoAFavor(Long id) {
        repository.findById(id).ifPresent(p -> {
            BigDecimal montoAplicado = p.getMontoAplicado() != null ? p.getMontoAplicado() : BigDecimal.ZERO;

            if (p.getTratamiento() != null && p.getTratamiento().getId() != null) {
                tratamientoRepository.findById(p.getTratamiento().getId()).ifPresent(t -> {
                    BigDecimal totalCobrado = t.getTotalCobrado() != null ? t.getTotalCobrado() : BigDecimal.ZERO;
                    t.setTotalCobrado(totalCobrado.subtract(montoAplicado).max(BigDecimal.ZERO));
                    tratamientoRepository.save(t);
                });
            } else if (p.getCita() != null && p.getCita().getId() != null) {
                citaRepository.findById(p.getCita().getId()).ifPresent(cita -> {
                    if (cita.getPrecio() == null || cita.getPrecio().compareTo(BigDecimal.ZERO) <= 0) return;
                    BigDecimal montoPagado = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
                    BigDecimal nuevo = montoPagado.subtract(montoAplicado).max(BigDecimal.ZERO);
                    cita.setMontoPagado(nuevo);
                    cita.setEstadoPago(catEstadoPagoCitaRepository.findByKey(
                            estadoPagoPorMonto(nuevo, cita.getPrecio())).orElse(null));
                    citaRepository.save(cita);
                });
            }

            if (montoAplicado.compareTo(BigDecimal.ZERO) > 0 && p.getPaciente() != null && p.getPaciente().getId() != null) {
                pacienteRepository.findById(p.getPaciente().getId()).ifPresent(paciente -> {
                    BigDecimal saldo = paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO;
                    BigDecimal nuevo = saldo.add(montoAplicado);
                    paciente.setSaldoAFavor(nuevo);
                    pacienteRepository.save(paciente);
                    saldoMovimientoService.registrar(paciente, montoAplicado, nuevo,
                            "Anulación de cita — el pago quedó a favor", p.getCita(), p);
                });
            }
        });
    }
}
