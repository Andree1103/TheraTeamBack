package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una cuenta de crédito con su deuda al día.
 *
 * Es la fila del reporte de cobranza: quién debe, de qué tipo, cuánto y desde cuándo. Filtrando
 * por tipo sale "qué trabajadores deben" o "qué pacientes deben", que son los dos reportes que
 * pidió la clínica, con una sola pantalla.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CuentaCreditoDTO {
    private Long id;

    private Long titularUsuarioId;
    private String titularNombre;
    private String titularEmail;
    /** El rol del titular, para que la pantalla distinga un terapeuta de un paciente de un vistazo. */
    private String titularRol;
    /** Si el titular es terapeuta, su id — así el reporte enlaza a su ficha. */
    private Long titularTerapeutaId;

    private String tipo;
    private BigDecimal limite;
    private Boolean activo;
    private String notas;

    /** Cuánto debe AHORA: citas sueltas + paquetes. No se guarda, se suma. */
    private BigDecimal deuda;
    /** El desglose, porque un paquete y una cita suelta se cobran distinto. */
    private BigDecimal deudaEnCitas;
    private BigDecimal deudaEnPaquetes;

    private long citas;
    private long paquetes;
    /** Cuántos pacientes cuelgan de la cuenta, vigentes. */
    private long pacientes;

    /** La fecha más antigua que sigue sin cobrarse. Es lo que dice si una cuenta se está añejando. */
    private LocalDateTime desde;

    /** true si la deuda se pasó del límite. Avisa, no bloquea. */
    private boolean sePasaDelLimite;
}
