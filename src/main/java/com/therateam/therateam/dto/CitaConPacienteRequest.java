package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CitaConPacienteRequest {

    private PacienteInput paciente;

    /** Segundo paciente — solo para sesiones tipo multipaciente (puede ser null) */
    private PacienteInput paciente2;

    private String terapeutaNombre;
    private String tipoKey;
    private LocalDateTime fechaInicio;
    private Integer duracionMinutos;
    private String estadoKey;
    private String observacion;

    /** Opcional: clave de modalidad (PRESENCIAL, ONLINE, etc.) */
    private String modalidadKey;

    /** Clasificación de la cita: FIJO, EVENTUAL o SOLO_HOY. Default EVENTUAL si no se envía. */
    private String tipoRecurrencia;

    /** Total de sesiones del plan — usado para fijar totalSesiones al crear tratamiento */
    private Integer totalSesionesPlan;

    /** Precio por sesión del plan */
    private java.math.BigDecimal precioPorSesion;

    /**
     * Opcional: id de un tratamiento YA EXISTENTE del paciente al que enganchar estas citas
     * (en vez de buscar/crear uno por terapeuta+tipoTerapia). Útil cuando el tratamiento ya
     * tiene sesiones pagadas por adelantado — las citas que caigan dentro de lo ya cobrado
     * se marcan PAGADA automáticamente, sin generar un pago nuevo.
     */
    private Long tratamientoId;

    /**
     * Agrupador liviano opcional para "citas masivas": el caller genera un id (ej. un UUID) y lo
     * manda igual en todas las citas del mismo lote — no crea ningún Tratamiento ni toca el
     * catálogo de Paquetes, solo permite luego contar cuántas del grupo faltan/ya se atendieron.
     */
    private String loteMasivoId;
}
