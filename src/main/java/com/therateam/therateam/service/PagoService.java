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
import com.therateam.therateam.repository.SaldoMovimientoRepository;
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
    private final SaldoMovimientoRepository saldoMovimientoRepository;
    private final VentaService ventaService;
    private final com.therateam.therateam.repository.CierreCajaRepository cierreCajaRepository;
    private final CajaService cajaService;

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

        // Se fija aqui si este cobro trae dinero, con el metodo CARGADO de la base.
        //
        // No vale hacerlo en @PrePersist: ahi `metodo` es todavia el cascaron que deserializo
        // Jackson desde {"metodo":{"id":9}}, con cuentaEnCaja a null — y un null se lee como
        // "si es dinero". Resultado: un cobro con el metodo "Sin pago" se grababa como si
        // hubiera entrado plata, y al anular la cita devolvia ese dinero inventado al saldo.
        // Lo cazo la bateria (caso 6) y por eso existe ese caso.
        if (p.getMetodo() != null && p.getMetodo().getId() != null) {
            p.setTrajoDinero(catMetodoPagoRepository.findById(p.getMetodo().getId())
                    .map(CatMetodoPago::cuentaEnCajaOEsDinero)
                    .orElse(true));
        } else {
            p.setTrajoDinero(false);   // sin metodo = salio del saldo a favor, no entro nada
        }

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

        // Cuanto del saldo se pone sobre la mesa lo decide quien cobra, no este metodo.
        //
        // Antes el saldo entero entraba SIEMPRE en el reparto: disponible = recibido + saldo, y
        // se gastaba lo que cupiera en la deuda. Nadie podia decir que no. Paso de verdad: un
        // paquete de 5x47 con un cobro de S/ 45 registrado se llevo por delante los S/ 190 que
        // el paciente tenia a favor, dejo las cinco sesiones PAGADAS y la deuda en cero. El
        // operador apunto 45; el sistema gasto 235.
        //
        // Con saldoAAplicar en null se mantiene el comportamiento de antes, para no cambiarle el
        // significado a las llamadas que todavia no lo mandan. Quien lo manda, manda: 0 significa
        // "no toques su saldo", y el sobrante no ofrecido se le devuelve intacto mas abajo.
        BigDecimal saldoOfrecido = p.getSaldoAAplicar() == null
                ? saldoPrevio
                : p.getSaldoAAplicar().max(BigDecimal.ZERO).min(saldoPrevio);
        BigDecimal saldoReservado = saldoPrevio.subtract(saldoOfrecido);
        BigDecimal montoDisponible = montoRecibido.add(saldoOfrecido);

        Cita citaAsociada = null;
        if (p.getCita() != null && p.getCita().getId() != null) {
            citaAsociada = citaRepository.findById(p.getCita().getId()).orElse(null);
        }
        // Una cita suelta puede no tener precio: solo se le pone uno automatico a los tipos de
        // terapia que tienen precio_recomendado cargado, y ademas solo un admin puede fijarlo al
        // crearla. Sin esto, cobrarla era imposible — el monto entraba como saldo a favor y la
        // cita quedaba SIN_PAGO para siempre.
        //
        // Regla: si no tiene precio, el monto que se cobra PASA A SER su precio. Es la unica
        // lectura razonable (no hay otro total contra el cual comparar) y deja la cita cuadrada:
        // precio = pagado = PAGADA. Si despues resulta que era un adelanto, se corrige el precio
        // desde la cita y el estado de pago se recalcula solo.
        //
        // El cobro puede no traer dinero nuevo: si se paga con el saldo a favor, montoRecibido es
        // 0 y lo que se pretende cobrar viene en montoAplicado. Sin mirar ese campo, una cita sin
        // precio pagada con saldo no llegaba a tener precio, no habia deuda que cubrir, el saldo
        // no se tocaba... y aun asi la cita acababa en PAGADA por el bloque del final. Un pago
        // fantasma: cita saldada, cero soles movidos.
        // Se toma el MAYOR de los dos, no el efectivo a secas: en un cobro mixto (parte con saldo,
        // parte en efectivo) el efectivo es solo un trozo del precio. Cobrar 60 poniendo 40 en
        // efectivo y 20 del saldo dejaria la cita valiendo 40 si mirasemos solo lo recibido.
        BigDecimal cargoSolicitado = montoRecibido
                .max(p.getMontoAplicado() != null ? p.getMontoAplicado() : BigDecimal.ZERO)
                .max(BigDecimal.ZERO);

        if (citaAsociada != null
                && (p.getTratamiento() == null || p.getTratamiento().getId() == null)
                && (citaAsociada.getPrecio() == null || citaAsociada.getPrecio().compareTo(BigDecimal.ZERO) <= 0)
                && cargoSolicitado.compareTo(BigDecimal.ZERO) > 0) {
            citaAsociada.setPrecio(cargoSolicitado);
            citaRepository.save(citaAsociada);
        }

        boolean citaConPrecioDeReferencia = citaAsociada != null && citaAsociada.getPrecio() != null
                && citaAsociada.getPrecio().compareTo(BigDecimal.ZERO) > 0;

        Tratamiento tratamiento = null;
        BigDecimal precioReferencia = BigDecimal.ZERO;
        BigDecimal deudaPendiente;
        // Solo la rama de adelantos (cita/paquete con deuda propia) deja el estado_pago ya
        // resuelto — un "adelanto general" sin ninguno de los dos no toca ninguna cita.
        boolean resueltoPorAdelanto = false;

        // El paquete al que se abona lo manda la cita, no quien llama.
        //
        // Si el pago es de una cita que pertenece a un paquete, ese es el paquete que se cobra,
        // aunque en el cuerpo venga otro. La pantalla mandaba el paquete MAS RECIENTE del
        // paciente: con dos paquetes, pagar una sesion del viejo abonaba al nuevo, y si el nuevo
        // ya estaba pagado no quedaba deuda que cubrir — el dinero entero se iba a saldo a favor
        // (monto_aplicado 0) y la cita se quedaba SIN_PAGO por mucho que se pagara.
        Long tratamientoDeLaCita = citaAsociada != null && citaAsociada.getSesion() != null
                && citaAsociada.getSesion().getTratamiento() != null
                ? citaAsociada.getSesion().getTratamiento().getId() : null;
        Long tratamientoId = tratamientoDeLaCita != null ? tratamientoDeLaCita
                : (p.getTratamiento() != null ? p.getTratamiento().getId() : null);

        if (tratamientoId != null) {
            final Long idPaquete = tratamientoId;
            tratamiento = tratamientoRepository.findById(idPaquete)
                    .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado: " + idPaquete));
            p.setTratamiento(tratamiento);
            precioReferencia = tratamiento.getPrecioPorSesion() != null ? tratamiento.getPrecioPorSesion() : BigDecimal.ZERO;
            int totalSesiones = tratamiento.getTotalSesiones() != null ? tratamiento.getTotalSesiones() : 0;
            BigDecimal montoTotal   = precioReferencia.multiply(BigDecimal.valueOf(totalSesiones));
            BigDecimal totalCobrado = tratamiento.getTotalCobrado() != null ? tratamiento.getTotalCobrado() : BigDecimal.ZERO;
            deudaPendiente = montoTotal.subtract(totalCobrado).max(BigDecimal.ZERO);

            // Si el pago apunta a UNA sesion, la deuda a cubrir es la de esa sesion, no la del
            // paquete entero.
            //
            // Sin esto el dinero se evaporaba: con un paquete de 3x50 y un saldo de 120, pagar la
            // primera sesion aplicaba 120 (la deuda del paquete, 150, daba de sobra), y abajo
            // aplicarMontoACita topa la cita en su precio — la cita se quedaba con 50 y los otros
            // 70 no llegaban a ninguna sesion, pero total_cobrado SI subia 120. El paquete decia
            // tener cobrado mas de lo que sus sesiones mostraban, y esa diferencia no estaba en
            // ningun sitio: ni en una cita, ni en el saldo del paciente.
            //
            // Ahora el excedente se queda a favor del paciente, que es donde se puede volver a
            // usar. En produccion nunca llego a pasar (cero paquetes descuadrados en el volcado
            // del 06/10) porque hacia falta saldo o un cobro de mas en esa pantalla; se tapa
            // porque pagar sesiones marcadas con el saldo ya es un camino normal.
            if (citaAsociada != null) {
                BigDecimal precioDeLaCita = citaAsociada.getPrecio() != null
                        && citaAsociada.getPrecio().compareTo(BigDecimal.ZERO) > 0
                        ? citaAsociada.getPrecio() : precioReferencia;
                BigDecimal yaPagadoDeLaCita = citaAsociada.getMontoPagado() != null
                        ? citaAsociada.getMontoPagado() : BigDecimal.ZERO;
                deudaPendiente = deudaPendiente.min(
                        precioDeLaCita.subtract(yaPagadoDeLaCita).max(BigDecimal.ZERO));
            }
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
        // Lo que sobra de lo ofrecido vuelve a favor, y con ello lo que ni siquiera se ofrecio:
        // el saldo reservado no participa del cobro, pero sigue siendo suyo.
        BigDecimal saldoGenerado = montoDisponible.subtract(montoAplicado).add(saldoReservado);

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
        // ...pero solo si de verdad se cobro algo. Un pago de 0 sobre una cita sin precio no
        // salda nada, y marcarla PAGADA dejaba la cita cerrada sin un sol detras.
        if (!resueltoPorAdelanto && montoAplicado.compareTo(BigDecimal.ZERO) > 0
                && saved.getCita() != null && saved.getCita().getId() != null) {
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

    /**
     * Cuanto movio este pago el saldo a favor del paciente, con signo.
     *
     * Positivo = se lo dejo a favor (un adelanto). Negativo = se lo consumio para cubrir una
     * deuda. Cero = ni lo toco.
     */
    private BigDecimal efectoSobreElSaldo(Pago p) {
        BigDecimal generado = p.getSaldoGenerado() != null ? p.getSaldoGenerado() : BigDecimal.ZERO;
        BigDecimal previo   = p.getSaldoPrevio()   != null ? p.getSaldoPrevio()   : BigDecimal.ZERO;
        return generado.subtract(previo);
    }

    /**
     * Un pago de una caja ya cerrada no se toca.
     *
     * Cerrar la caja es firmar que ese dinero es el que habia. Borrar despues un pago de ese
     * turno cambia hacia atras un total que alguien ya cuadro y firmo, y deja el arqueo sin
     * cuadrar contra su propio cierre. La correccion de un cobro de un dia cerrado se hace con un
     * movimiento nuevo con fecha de hoy (una devolucion), no reescribiendo el pasado.
     *
     * El turno se calcula igual que en Caja: hasta la hora de corte configurada es el 1, desde
     * ahi el 2. Si esa regla cambia, cambia en CajaService y esto la sigue.
     */
    private void verificarQueLaCajaSigaAbierta(Pago p) {
        if (p.getFechaPago() == null) return;
        java.time.LocalDate dia = p.getFechaPago().toLocalDate();
        int turno = p.getFechaPago().toLocalTime().isBefore(cajaService.horaCorte()) ? 1 : 2;
        cierreCajaRepository.findByFechaAndTurno(dia, turno).ifPresent(cierre -> {
            throw new IllegalArgumentException(String.format(
                    "No se puede eliminar: la caja del %s (turno %d) ya esta cerrada. "
                  + "Para corregirlo, registra una devolucion con fecha de hoy.",
                    dia.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), turno));
        });
    }

    /**
     * No se puede borrar un pago cuyo saldo a favor ya se gasto.
     *
     * Si un adelanto de 200 dejo saldo y el paciente ya uso 180 en citas, deshacerlo dejaria el
     * saldo en -180: dinero que la clinica ya conto como cobrado y que ninguna pantalla sabe
     * representar. Se corta aqui y se dice cuanto queda, para que primero se deshagan los cobros
     * que lo consumieron. El caso contrario —un pago que USO saldo— siempre se puede deshacer:
     * devolverlo solo sube el saldo.
     */
    private void verificarQueElSaldoSigaDisponible(Pago p) {
        BigDecimal efecto = efectoSobreElSaldo(p);
        if (efecto.signum() <= 0) return;
        if (p.getPaciente() == null || p.getPaciente().getId() == null) return;
        pacienteRepository.findById(p.getPaciente().getId()).ifPresent(paciente -> {
            BigDecimal actual = paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO;
            if (actual.compareTo(efecto) < 0) {
                throw new IllegalArgumentException(String.format(
                        "No se puede eliminar este pago: dejo S/ %s a favor del paciente y solo quedan "
                      + "S/ %s sin usar. Deshaz primero los cobros que usaron ese saldo.",
                        efecto, actual));
            }
        });
    }

    /** Revierte el efecto de un pago sobre el saldo del paciente y el tratamiento/cita (usado al eliminar). */
    private void revertir(Pago p) {
        // Si el pago era una venta, los productos volvieron al estante: el contador debe subir.
        if (p.getId() != null) ventaService.devolverStock(p.getId());
        if (p.getPaciente() != null && p.getPaciente().getId() != null) {
            pacienteRepository.findById(p.getPaciente().getId()).ifPresent(paciente -> {
                // Se DESHACE lo que hizo este pago, no se restaura una foto vieja.
                //
                // Antes esto hacia `setSaldoAFavor(p.getSaldoPrevio())`: pisaba el saldo actual con
                // el que habia el dia en que se creo el pago, tirando todo lo ocurrido despues.
                // Borrar un pago podia SUBIR el saldo (si el saldo de entonces era mayor), bajarlo
                // de golpe, o moverlo por un importe que no tenia nada que ver con el pago borrado.
                // Solo acertaba si era el ultimo pago del paciente y nada mas habia pasado.
                //
                // El efecto de un pago sobre el saldo es `saldoGenerado - saldoPrevio`: un adelanto
                // de 200 sobre saldo 0 vale +200; un cobro que consumio 20 de 170 vale -20.
                // Deshacerlo es restar ese efecto al saldo de AHORA, que es lo unico que compone
                // bien con los movimientos posteriores.
                BigDecimal actual = paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO;
                BigDecimal efecto = efectoSobreElSaldo(p);
                BigDecimal nuevo = actual.subtract(efecto);
                paciente.setSaldoAFavor(nuevo);
                pacienteRepository.save(paciente);
                String detalle = efecto.signum() > 0
                        ? " — se retiran S/ " + efecto + " que habia dejado a favor"
                        : (efecto.signum() < 0
                            ? " — se devuelven S/ " + efecto.negate() + " de saldo que habia usado"
                            : " — sin efecto sobre su saldo");
                saldoMovimientoService.registrar(paciente, efecto.negate(), nuevo,
                        "Se eliminó el pago #" + p.getId() + detalle, p.getCita(), null);
            });
        }
        BigDecimal montoAplicado = p.getMontoAplicado() != null ? p.getMontoAplicado() : BigDecimal.ZERO;

        if (p.getTratamiento() != null && p.getTratamiento().getId() != null) {
            tratamientoRepository.findById(p.getTratamiento().getId()).ifPresent(t -> {
                BigDecimal totalCobrado = t.getTotalCobrado() != null ? t.getTotalCobrado() : BigDecimal.ZERO;
                t.setTotalCobrado(totalCobrado.subtract(montoAplicado).max(BigDecimal.ZERO));
                tratamientoRepository.save(t);

                // Al cobrar, el monto no se quedó en el total del paquete: se repartió entre las
                // citas de sus sesiones (aplicarMontoACita / repartirEntreSesiones). Si al
                // eliminar el pago solo se baja totalCobrado, las citas siguen diciendo PAGADA
                // sin ningún pago que las respalde — que es justo lo que se veía en el perfil.
                BigDecimal precioRef = t.getPrecioPorSesion() != null ? t.getPrecioPorSesion() : BigDecimal.ZERO;
                if (p.getCita() != null && p.getCita().getId() != null) {
                    quitarMontoDeCita(p.getCita().getId(), montoAplicado, precioRef);
                } else {
                    quitarDeSesiones(t.getId(), montoAplicado, precioRef);
                }
            });
            return;
        }
        if (p.getCita() != null && p.getCita().getId() != null) {
            quitarMontoDeCita(p.getCita().getId(), montoAplicado, BigDecimal.ZERO);
        }
    }

    /** Inverso de {@link #aplicarMontoACita}: le quita `monto` a lo pagado de la cita y recalcula
     *  su estado. Con precio 0 o sin precio no hay nada que cubrir, así que queda SIN_PAGO —
     *  antes se devolvía sin tocarla y la cita se quedaba marcada como PAGADA. */
    private void quitarMontoDeCita(Long citaId, BigDecimal monto, BigDecimal precioReferencia) {
        citaRepository.findById(citaId).ifPresent(cita -> {
            BigDecimal precio = cita.getPrecio() != null && cita.getPrecio().compareTo(BigDecimal.ZERO) > 0
                    ? cita.getPrecio() : precioReferencia;
            BigDecimal montoPagado = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal nuevo = montoPagado.subtract(monto).max(BigDecimal.ZERO);
            cita.setMontoPagado(nuevo);
            // Con precio 0 no se puede usar estadoPagoPorMonto: su regla "pagado >= precio" daría
            // PAGADA con 0 de 0, que es justo el estado equivocado del que se venía.
            String estado = precio.compareTo(BigDecimal.ZERO) <= 0 ? "SIN_PAGO" : estadoPagoPorMonto(nuevo, precio);
            cita.setEstadoPago(catEstadoPagoCitaRepository.findByKey(estado).orElse(null));
            citaRepository.save(cita);
        });
    }

    /** Inverso de {@link #repartirEntreSesiones}: como el reparto llena las sesiones en orden,
     *  al deshacerlo se descuenta desde la última hacia atrás, para que el paquete quede igual
     *  que antes del pago y no con las primeras sesiones a medio pagar. */
    private void quitarDeSesiones(Long tratamientoId, BigDecimal monto, BigDecimal precioReferencia) {
        BigDecimal restante = monto;
        List<Sesion> sesiones = new java.util.ArrayList<>(sesionRepository.findByTratamientoIdWithCita(tratamientoId));
        java.util.Collections.reverse(sesiones);
        for (Sesion s : sesiones) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) break;
            Cita cita = s.getCitaActiva();
            if (cita == null) continue;
            BigDecimal montoPagado = cita.getMontoPagado() != null ? cita.getMontoPagado() : BigDecimal.ZERO;
            if (montoPagado.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal aQuitar = restante.min(montoPagado);
            quitarMontoDeCita(cita.getId(), aQuitar, precioReferencia);
            restante = restante.subtract(aQuitar);
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

        // Solo se devuelve en mano lo que entro en mano.
        //
        // revertir() ya le ha restituido el saldo que este cobro le consumio. Si ademas se
        // grabara una devolucion por el importe aplicado, la parte pagada con su propio saldo
        // contaria dos veces: le vuelve el credito Y los libros dicen que salio plata por el
        // mostrador. Un cobro de 235 hecho con 45 en efectivo y 190 de su saldo devuelve 45;
        // los 190 ya estan de vuelta donde estaban.
        BigDecimal montoDevuelto = original.elEfectivoQueEntro()
                .min(original.getMontoAplicado() != null ? original.getMontoAplicado() : BigDecimal.ZERO);
        if (montoDevuelto.compareTo(BigDecimal.ZERO) <= 0) {
            // No entro efectivo: no hay nada que sacar del cajon. El cobro queda deshecho y el
            // saldo restituido, que es toda la devolucion posible.
            return null;
        }
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
        // Metodo propio "Devolucion", no el del cobro original: una salida no es una entrada, y
        // al heredar un metodo que cuenta en caja la devolucion se metia en el arqueo del dia
        // restando de lo cobrado hoy, que es dinero de otro dia. Por donde salio queda en notas.
        devolucion.setMetodo(metodoDevolucion());
        devolucion.setTrajoDinero(false);
        devolucion.setMontoRecibido(montoDevuelto);
        devolucion.setMontoAplicado(BigDecimal.ZERO);
        devolucion.setSaldoGenerado(BigDecimal.ZERO);
        devolucion.setSaldoPrevio(saldoActual);
        devolucion.setEsDevolucion(true);
        devolucion.setPagoOrigenId(original.getId());
        devolucion.setConcepto("Devolución del pago #" + original.getId());
        devolucion.setNotas("Devolución de dinero por anulación de cita — salió por: "
                + (original.getMetodo() != null && original.getMetodo().getNombre() != null
                   ? original.getMetodo().getNombre().trim() : "sin método registrado"));
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
        // El metodo que llega dice por DONDE salio el dinero; la fila se graba como "Devolucion"
        // para que no se confunda con un cobro ni entre en el arqueo del dia.
        CatMetodoPago porDondeSalio = metodoId != null ? catMetodoPagoRepository.findById(metodoId).orElse(null) : null;
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
        devolucion.setMetodo(metodoDevolucion());
        devolucion.setTrajoDinero(false);
        devolucion.setMontoRecibido(monto);
        devolucion.setMontoAplicado(BigDecimal.ZERO);
        devolucion.setSaldoGenerado(BigDecimal.ZERO);
        devolucion.setSaldoPrevio(saldoActual);
        devolucion.setEsDevolucion(true);
        devolucion.setConcepto(concepto);
        devolucion.setNotas(concepto + " — salió por: "
                + (porDondeSalio != null && porDondeSalio.getNombre() != null
                   ? porDondeSalio.getNombre().trim() : "sin método registrado"));
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

    /**
     * ¿Al paquete entro dinero de verdad alguna vez?
     *
     * Mismo criterio que para una cita suelta: un paquete "cobrado" con el metodo "Sin pago" o
     * "Paquete" no trajo un sol — es una anotacion. Si al anular una de sus sesiones se devuelve
     * ese importe como saldo a favor, se le regala al paciente un credito que puede gastar
     * contra dinero que nunca existio.
     *
     * Basta con que UNO de sus pagos haya sido dinero: ahi si hay algo que devolver, y partirlo
     * sesion por sesion seria precision falsa sobre un reparto que el sistema no guarda.
     */
    /**
     * El metodo "Devolucion". Esta marcado cuenta_en_caja = false, asi que las devoluciones no
     * entran en el arqueo del dia — el cierre las lista aparte, con el resto de lo que no es
     * dinero de hoy.
     *
     * Si no estuviera (base sin la migracion), se devuelve null: el pago se graba igual y la
     * devolucion no se pierde, solo queda sin metodo.
     */
    private CatMetodoPago metodoDevolucion() {
        return catMetodoPagoRepository.findAll().stream()
                .filter(m -> "DEVOLUCION".equalsIgnoreCase(m.getKey() == null ? "" : m.getKey().trim()))
                .findFirst().orElse(null);
    }

    /**
     * Si el paciente puso dinero por este paquete — en efectivo o de su saldo a favor.
     *
     * Antes pedia montoRecibido > 0 Y que el metodo contara en caja, asi que un paquete pagado
     * con el saldo del propio paciente (recibido 0, sin metodo) daba NO por partida doble. Al
     * anular una sesion de ese paquete no se le devolvia nada.
     */
    public boolean elPaqueteRecibioDinero(Long tratamientoId) {
        return repository.findByTratamientoId(tratamientoId).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getEsDevolucion()))
                .anyMatch(p -> p.loQuePusoElPaciente().compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * Elimina el pago y deshace todo su efecto: saldo del paciente, total del paquete y estado de
     * pago de las citas que cubría.
     *
     * Es @Transactional a propósito. Sin eso, si el DELETE fallaba (ver abajo) lo que revertir()
     * ya había escrito quedaba grabado igual: el paquete bajaba su total cobrado, el pago seguía
     * existiendo y las citas seguían PAGADA — la base terminaba peor que antes de intentarlo.
     */
    @Transactional
    public boolean delete(Long id) {
        return repository.findById(id).map(p -> {
            // Antes de tocar nada: la caja de ese dia no puede estar cerrada, y su saldo no
            // puede estar ya gastado. Las dos cosas dejarian numeros que nadie puede cuadrar.
            verificarQueLaCajaSigaAbierta(p);
            verificarQueElSaldoSigaDisponible(p);
            revertir(p);
            // saldo_movimientos.pago_id no tiene ON DELETE, así que el movimiento que dejó este
            // pago impedía borrarlo: el error salía como "Los datos enviados no son válidos",
            // que no dice nada. Pasa siempre que el paciente pagó de más o usó saldo a favor.
            // Se desligan en vez de borrarse — revertir() ya dejó anotado el movimiento que
            // explica la devolución y el historial del paciente no debe perder ninguna línea.
            saldoMovimientoRepository.desligarDelPago(id);
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
        revertirComoSaldoAFavor(id, "Anulación de cita — el pago quedó a favor");
    }

    /**
     * Igual que el anterior, nombrando el hecho en el historial de saldo del paciente.
     *
     * Hace falta porque el mismo movimiento de dinero lo provocan cosas distintas — anular la
     * cita o marcar una inasistencia con devolucion — y en Adelantos hay que poder distinguirlas.
     */
    @Transactional
    public void revertirComoSaldoAFavor(Long id, String motivoMovimiento) {
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

            // Se le devuelve lo que PUSO, no lo que vio la caja.
            //
            // Un cobro con "Sin pago" o "Paquete" no puso nada: devolverlo como saldo le regalaba
            // un credito contra dinero que no existe —una cita asi, anulada, dejaba S/ 50 a favor
            // de la nada, y dos de esas S/ 100—. Eso sigue dando cero.
            //
            // Pero la pregunta estaba mal hecha: se medía "¿entro efectivo con este pago?", y un
            // cobro pagado con el saldo del propio paciente responde que no. Resultado: se le
            // comia el saldo al cobrar y no se lo devolvia al anular. Lo que cuenta es su aporte:
            // el efectivo de entonces mas el saldo suyo que se consumio.
            BigDecimal aDevolver = p.loQuePusoElPaciente().min(montoAplicado);

            if (aDevolver.compareTo(BigDecimal.ZERO) > 0
                    && p.getPaciente() != null && p.getPaciente().getId() != null) {
                pacienteRepository.findById(p.getPaciente().getId()).ifPresent(paciente -> {
                    BigDecimal saldo = paciente.getSaldoAFavor() != null ? paciente.getSaldoAFavor() : BigDecimal.ZERO;
                    BigDecimal nuevo = saldo.add(aDevolver);
                    paciente.setSaldoAFavor(nuevo);
                    pacienteRepository.save(paciente);
                    saldoMovimientoService.registrar(paciente, aDevolver, nuevo,
                            motivoMovimiento, p.getCita(), p);
                });
            }
        });
    }
}
