package com.therateam.therateam.service;

import com.therateam.therateam.model.Asiento;
import com.therateam.therateam.model.AsientoLinea;
import com.therateam.therateam.model.CatMetodoPago;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.repository.AsientoLineaRepository;
import com.therateam.therateam.repository.AsientoRepository;
import com.therateam.therateam.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.therateam.therateam.model.AsientoLinea.Concepto.*;

/**
 * Escribe el libro de movimientos. Fase 1: solo escribe, nadie lee.
 *
 * POR QUÉ CADA LLAMADA VA EN SU PROPIA TRANSACCIÓN
 *
 * Mientras los dos mundos conviven, el libro no puede tumbar un cobro. Si un asiento sale mal
 * —porque falta cubrir un camino, o porque la ecuación no cuadra— el cobro tiene que grabarse
 * igual: el dinero del mostrador no espera a que terminemos la migración. Por eso REQUIRES_NEW
 * y por eso se traga la excepción, dejando rastro en el log.
 *
 * Eso NO es tapar el problema: el asiento que falta aparece como diferencia en el reconciliador,
 * que es justamente lo que la fase 2 va a mirar a diario. Se pierde el asiento, no el aviso.
 *
 * En la fase 3, cuando el libro sea la fuente, esto pasa a la transacción del cobro y un asiento
 * que no cuadre tendrá que tumbarlo — que para entonces será lo correcto.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibroService {

    private final AsientoRepository asientoRepository;
    private final AsientoLineaRepository lineaRepository;

    /**
     * Un cobro, tal y como lo dejó el motor.
     *
     * Las cuatro piezas salen del propio pago y no hay que interpretarlas: lo recibido entra por
     * caja o queda como constancia según el método; lo que subió o bajó su saldo sale de comparar
     * saldo_previo con saldo_generado; y lo aplicado es la deuda que se cubrió.
     */
    public void cobro(Pago p) {
        enSuPropiaTransaccion(() -> {
            BigDecimal recibido = nvl(p.getMontoRecibido());
            BigDecimal previo   = nvl(p.getSaldoPrevio());
            BigDecimal generado = nvl(p.getSaldoGenerado());

            Asiento a = cabecera(Asiento.Tipo.COBRO, p, describir(p));
            if (p.trajoDineroDeVerdad()) a.linea(CAJA_ENTRA, recibido, metodoDe(p), null, null);
            else                         a.linea(CONSTANCIA, recibido);

            a.linea(CREDITO_USA,    previo.subtract(generado).max(BigDecimal.ZERO));
            a.linea(CREDITO_GENERA, generado.subtract(previo).max(BigDecimal.ZERO));
            a.linea(DEUDA_CUBRE,    nvl(p.getMontoAplicado()), null, citaDe(p), tratamientoDe(p));
            guardar(a);
        }, "cobro del pago #" + p.getId());
    }

    /**
     * Una devolución: sale dinero y, al otro lado, se libera la deuda que el cobro original había
     * cubierto. Lo que el paciente recupera como crédito, si lo hubiera, va en su propio asiento
     * de anulación — aquí solo consta lo que sale por el mostrador.
     */
    public void devolucion(Pago devolucion, Pago origen) {
        enSuPropiaTransaccion(() -> {
            BigDecimal importe = nvl(devolucion.getMontoRecibido());
            Asiento a = cabecera(Asiento.Tipo.DEVOLUCION, devolucion,
                    "Devolución del pago #" + (origen != null ? origen.getId() : "?"));
            a.linea(CAJA_SALE, importe, metodoDe(devolucion), null, null);
            // La contrapartida: de qué deuda salía ese dinero. Si el cobro original fue una
            // constancia, no había deuda real cubierta con plata — se libera la constancia.
            boolean huboDinero = origen == null || origen.trajoDineroDeVerdad();
            a.linea(huboDinero ? DEUDA_LIBERA : CONSTANCIA, importe, null,
                    huboDinero ? citaDe(devolucion) : null,
                    huboDinero ? tratamientoDe(devolucion) : null);
            guardar(a);
        }, "devolución #" + devolucion.getId());
    }

    /**
     * Una anulación que deja el dinero a favor del paciente: la deuda vuelve a deberse y el
     * importe se le reconoce como crédito.
     *
     * `conRespaldo` es lo que el paciente había puesto de verdad. Si el cobro era una constancia
     * no puso nada, así que no se le genera crédito: se libera la constancia y ya. Ese reparto
     * es el que estuvo mal toda la semana, y aquí la ecuación no deja escribirlo de otra forma.
     */
    public void anulacionAFavor(Long pacienteId, Long pagoId, BigDecimal importe,
                                BigDecimal conRespaldo, Long citaId, Long tratamientoId, String nota) {
        enSuPropiaTransaccion(() -> {
            BigDecimal total   = nvl(importe);
            BigDecimal credito = nvl(conRespaldo).min(total).max(BigDecimal.ZERO);
            Asiento a = new Asiento();
            a.setTipo(Asiento.Tipo.ANULACION);
            a.setFecha(LocalDateTime.now());
            a.setPacienteId(pacienteId);
            a.setPagoId(pagoId);
            a.setUsuarioId(SecurityUtils.currentUserId());
            a.setNota(nota);
            a.linea(DEUDA_LIBERA, total, null, citaId, tratamientoId);
            a.linea(CREDITO_GENERA, credito);
            // Lo que no tenía dinero detrás no se le puede reconocer: se retira la constancia.
            a.linea(CONSTANCIA_LIBERA, total.subtract(credito));
            guardar(a);
        }, "anulación del pago #" + pagoId);
    }

    /**
     * Deshacer un cobro: su asiento, del revés.
     *
     * Cada concepto tiene exactamente uno opuesto, y está en el otro lado de la ecuación:
     *
     *   CAJA_ENTRA  <-> CAJA_SALE          CREDITO_USA <-> CREDITO_GENERA
     *   CONSTANCIA  <-> CONSTANCIA_LIBERA  DEUDA_CUBRE <-> DEUDA_LIBERA
     *
     * Por eso el inverso cuadra sin que haya que pensarlo: lo que estaba a la izquierda pasa a
     * la derecha y al revés, así que las dos sumas se intercambian. Deshacer un cobro de 50
     * hecho con 20 en efectivo y 30 de saldo da, por construcción, "se libera deuda 50 = salen
     * 20 del cajón + vuelven 30 a su favor". Esa era justo la cuenta que estuvo mal toda la
     * semana, y aquí no hay nada que decidir.
     *
     * Lo usan tanto eliminar un pago como devolverlo: las dos cosas deshacen lo mismo, cambia
     * solo el porqué.
     */
    public void reverso(Pago original, String nota) {
        enSuPropiaTransaccion(() -> {
            BigDecimal recibido = nvl(original.getMontoRecibido());
            BigDecimal previo   = nvl(original.getSaldoPrevio());
            BigDecimal generado = nvl(original.getSaldoGenerado());

            Asiento a = cabecera(Asiento.Tipo.ANULACION, original, nota);
            a.setFecha(LocalDateTime.now()); // deshacerlo pasa hoy, no el día del cobro

            if (original.trajoDineroDeVerdad()) a.linea(CAJA_SALE, recibido, metodoDe(original), null, null);
            else                                a.linea(CONSTANCIA_LIBERA, recibido);

            a.linea(CREDITO_GENERA, previo.subtract(generado).max(BigDecimal.ZERO));
            a.linea(CREDITO_USA,    generado.subtract(previo).max(BigDecimal.ZERO));
            a.linea(DEUDA_LIBERA,   nvl(original.getMontoAplicado()), null,
                    citaDe(original), tratamientoDe(original));
            guardar(a);
        }, "reverso del pago #" + original.getId());
    }

    // ── Fontanería ───────────────────────────────────────────────────────────

    private Asiento cabecera(Asiento.Tipo tipo, Pago p, String nota) {
        Asiento a = new Asiento();
        a.setTipo(tipo);
        a.setFecha(p.getFechaPago() != null ? p.getFechaPago() : LocalDateTime.now());
        a.setPacienteId(p.getPaciente() != null ? p.getPaciente().getId() : null);
        a.setPagoId(p.getId());
        a.setUsuarioId(SecurityUtils.currentUserId());
        a.setNota(nota);
        return a;
    }

    /** Cabecera y líneas por separado: la línea necesita el id del asiento para colgarse. */
    private void guardar(Asiento a) {
        if (a.getPacienteId() == null) {
            log.warn("[libro] asiento sin paciente, no se guarda: {}", a.getNota());
            return;
        }
        if (a.getLineas().isEmpty()) return;
        Asiento guardado = asientoRepository.save(a);
        a.getLineas().forEach(l -> l.setAsientoId(guardado.getId()));
        lineaRepository.saveAll(a.getLineas());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void ejecutar(Runnable r) { r.run(); }

    private void enSuPropiaTransaccion(Runnable r, String que) {
        try {
            ejecutar(r);
        } catch (RuntimeException e) {
            // El cobro ya está grabado y no se toca. Lo que se pierde es el asiento, y eso lo
            // destapa el reconciliador — que para eso existe.
            log.error("[libro] no se pudo anotar {}: {}", que, e.getMessage());
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private static Long metodoDe(Pago p) {
        CatMetodoPago m = p.getMetodo();
        return m != null ? m.getId() : null;
    }
    private static Long citaDe(Pago p) { return p.getCita() != null ? p.getCita().getId() : null; }
    private static Long tratamientoDe(Pago p) {
        return p.getTratamiento() != null ? p.getTratamiento().getId() : null;
    }
    private static String describir(Pago p) {
        if (Boolean.TRUE.equals(p.getEsAdicional())) return "Cobro adicional";
        if (citaDe(p) != null) return "Cobro de cita #" + citaDe(p);
        if (tratamientoDe(p) != null) return "Cobro de paquete #" + tratamientoDe(p);
        return "Adelanto a cuenta";
    }
}
