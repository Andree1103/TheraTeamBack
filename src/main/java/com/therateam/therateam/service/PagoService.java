package com.therateam.therateam.service;

import com.therateam.therateam.dto.PagoDTO;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.repository.CatEstadoPagoCitaRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PagoRepository;
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
     * Aplica el pago contra la deuda pendiente del PAQUETE completo (precio total del paquete
     * menos lo ya cobrado) — no contra las sesiones atendidas. Un paquete se vende y se cobra
     * por adelantado (total o en cuotas); las sesiones se van consumiendo aparte, a su propio
     * ritmo, vía atención clínica. Usa primero el saldo a favor arrastrado; el excedente por
     * encima del precio total del paquete se guarda como nuevo saldo a favor.
     */
    @Transactional
    public Pago save(Pago p) {
        Cita citaAsociada = null;
        if (p.getCita() != null && p.getCita().getId() != null) {
            citaAsociada = citaRepository.findById(p.getCita().getId()).orElse(null);
        }
        boolean citaConPrecioDeReferencia = citaAsociada != null && citaAsociada.getPrecio() != null
                && citaAsociada.getPrecio().compareTo(BigDecimal.ZERO) > 0;
        // Solo la rama de adelantos (cita suelta, sin paquete) deja el estado_pago ya resuelto —
        // un pago de paquete NO debe entrar aquí aunque la cita también tenga su propio precio.
        boolean resueltoPorAdelanto = false;

        if (p.getTratamiento() != null && p.getTratamiento().getId() != null && p.getMontoRecibido() != null) {
            Tratamiento t = tratamientoRepository.findById(p.getTratamiento().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado"));

            BigDecimal precio        = t.getPrecioPorSesion()  != null ? t.getPrecioPorSesion()  : BigDecimal.ZERO;
            int totalSesiones        = t.getTotalSesiones()    != null ? t.getTotalSesiones()    : 0;
            BigDecimal montoTotal    = precio.multiply(BigDecimal.valueOf(totalSesiones));
            BigDecimal totalCobrado  = t.getTotalCobrado()     != null ? t.getTotalCobrado()     : BigDecimal.ZERO;
            BigDecimal saldoPrevio   = t.getSaldoAFavor()      != null ? t.getSaldoAFavor()      : BigDecimal.ZERO;

            BigDecimal deudaPendiente = montoTotal.subtract(totalCobrado);
            if (deudaPendiente.compareTo(BigDecimal.ZERO) < 0) deudaPendiente = BigDecimal.ZERO;

            BigDecimal montoDisponible = p.getMontoRecibido().add(saldoPrevio);
            BigDecimal montoAplicado   = montoDisponible.min(deudaPendiente);
            BigDecimal saldoGenerado   = montoDisponible.subtract(montoAplicado);

            p.setSaldoPrevio(saldoPrevio);
            p.setMontoAplicado(montoAplicado);
            p.setSaldoGenerado(saldoGenerado);

            t.setTotalCobrado(totalCobrado.add(montoAplicado));
            t.setSaldoAFavor(saldoGenerado);
            tratamientoRepository.save(t);
        } else if (p.getMontoRecibido() != null && citaConPrecioDeReferencia) {
            // Pago (adelanto o completo) de una cita suelta con precio propio: se acumula contra
            // ese precio — puede cubrirlo de a pocos (adelantos) hasta llegar a PAGADA.
            BigDecimal precio            = citaAsociada.getPrecio();
            BigDecimal montoPagadoActual = citaAsociada.getMontoPagado() != null ? citaAsociada.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal deudaPendiente    = precio.subtract(montoPagadoActual).max(BigDecimal.ZERO);
            BigDecimal montoAplicado     = p.getMontoRecibido().min(deudaPendiente);
            BigDecimal saldoGenerado     = p.getMontoRecibido().subtract(montoAplicado);

            p.setSaldoPrevio(BigDecimal.ZERO);
            p.setMontoAplicado(montoAplicado);
            p.setSaldoGenerado(saldoGenerado);

            BigDecimal montoPagadoNuevo = montoPagadoActual.add(montoAplicado);
            citaAsociada.setMontoPagado(montoPagadoNuevo);
            citaAsociada.setEstadoPago(catEstadoPagoCitaRepository.findByKey(
                    estadoPagoPorMonto(montoPagadoNuevo, precio)).orElse(null));
            citaRepository.save(citaAsociada);
            resueltoPorAdelanto = true;
        } else if (p.getMontoRecibido() != null) {
            // Pago normal sin precio de referencia en la cita (comportamiento previo: se aplica
            // todo el monto, sin arrastrar saldo a favor de ningún tratamiento).
            p.setSaldoPrevio(BigDecimal.ZERO);
            p.setMontoAplicado(p.getMontoRecibido());
            p.setSaldoGenerado(BigDecimal.ZERO);
        }

        Pago saved = repository.save(p);

        // La rama de adelantos (arriba) ya dejó el estado_pago correcto según lo cobrado; para el
        // resto de casos (paquete, o cita sin precio propio) se conserva el comportamiento previo:
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

    private String estadoPagoPorMonto(BigDecimal montoPagado, BigDecimal precio) {
        if (montoPagado.compareTo(precio) >= 0) return "PAGADA";
        if (montoPagado.compareTo(BigDecimal.ZERO) > 0) return "PARCIAL";
        return "SIN_PAGO";
    }

    /** Revierte el efecto de un pago sobre el tratamiento o la cita suelta (usado al eliminar). */
    private void revertir(Pago p) {
        if (p.getTratamiento() != null && p.getTratamiento().getId() != null) {
            tratamientoRepository.findById(p.getTratamiento().getId()).ifPresent(t -> {
                BigDecimal totalCobrado = t.getTotalCobrado() != null ? t.getTotalCobrado() : BigDecimal.ZERO;
                BigDecimal montoAplicado = p.getMontoAplicado() != null ? p.getMontoAplicado() : BigDecimal.ZERO;
                t.setTotalCobrado(totalCobrado.subtract(montoAplicado).max(BigDecimal.ZERO));
                // El saldo a favor que dejó este pago se retira; el saldo previo a este pago se restaura.
                t.setSaldoAFavor(p.getSaldoPrevio() != null ? p.getSaldoPrevio() : BigDecimal.ZERO);
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

    public boolean delete(Long id) {
        return repository.findById(id).map(p -> {
            revertir(p);
            repository.deleteById(id);
            return true;
        }).orElse(false);
    }
}
