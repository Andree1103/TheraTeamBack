package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Corrección administrativa de una cita ya atendida: sirve para arreglar lo que se cargó mal
 * (terapeuta equivocado, tipo de terapia que no era, precio mal tipeado, cobro registrado con
 * el método de pago incorrecto).
 *
 * Es un endpoint aparte de la edición normal a propósito: la edición corriente PROHÍBE tocar
 * estas cosas en una cita atendida, y eso está bien como regla general. Esto es la excepción
 * explícita para administradores, y queda registrada en el historial de la cita.
 *
 * Todos los campos son opcionales: solo se aplica lo que venga.
 */
@Data
@NoArgsConstructor
public class CorreccionAtencionRequest {

    private Long terapeutaId;
    private String tipoTerapiaKey;
    private BigDecimal precio;
    /** Método del último pago real de la cita (no de devoluciones ni cobros adicionales). */
    private Long metodoPagoId;
    /** Por qué se corrige — queda en el historial de la cita. */
    private String motivo;
}
