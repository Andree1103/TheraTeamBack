package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mover una cita a otro momento: la original se queda como constancia y nace una nueva.
 *
 * Solo viajan los datos que pueden cambiar al reprogramar. El paciente, la terapia y el precio
 * no están aquí a propósito: los hereda la cita nueva. Cambiar eso de paso convertiría un
 * "vengo el jueves" en otra cita distinta, y para eso está la edición.
 */
@Data
@NoArgsConstructor
public class ReprogramarCitaRequest {
    private LocalDateTime fechaInicio;
    /** Opcional: si no viene, se calcula con la duración de la cita original. */
    private LocalDateTime fechaFin;
    /** Opcional: si no viene, se conserva la de la cita original. */
    private Integer duracionMinutos;
    /** Opcional: reprogramar con otro terapeuta es habitual (vacaciones, cambio de turno). */
    private Long terapeutaId;
    /** Obligatorio. */
    private String motivo;
}
