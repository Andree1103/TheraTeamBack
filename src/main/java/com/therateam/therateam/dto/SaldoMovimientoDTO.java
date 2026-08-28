package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento de saldo ya aplanado.
 *
 * Se devuelve esto y no la entidad porque SaldoMovimiento tiene relaciones perezosas
 * (paciente, cita, pago, terapeuta) que al serializarse fuera de la transacción rompen
 * con LazyInitializationException.
 */
@Data
@AllArgsConstructor
public class SaldoMovimientoDTO {
    private Long id;
    private Long pacienteId;
    private BigDecimal monto;
    private BigDecimal saldoResultante;
    private String motivo;
    private String terapeutaNombre;
    private Long citaId;
    private Long pagoId;
    private LocalDateTime fecha;
}
