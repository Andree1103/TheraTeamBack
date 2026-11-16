package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una línea del detalle de una cuenta: qué se atendió sin cobrar y por quién.
 *
 * Mezcla citas sueltas y paquetes en una sola lista porque es como se cobra —de una vez, todo lo
 * del mes— y porque la pregunta de la clínica ("qué le debo a fin de mes") no distingue. El campo
 * {@code tipo} dice cuál es cuál.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreditoLineaDTO {

    /** CITA o PAQUETE. */
    private String tipo;
    private Long id;

    private LocalDateTime fecha;

    private Long pacienteId;
    /** De quién fue la atención. En una cuenta de trabajador casi nunca es el titular. */
    private String pacienteNombre;

    private String terapeutaNombre;
    private String descripcion;

    /** El estado de la cita o del paquete, para distinguir una inasistencia de una atención. */
    private String estado;
    private String estadoPago;

    private BigDecimal monto;
    private BigDecimal pagado;
    private BigDecimal deuda;
}
