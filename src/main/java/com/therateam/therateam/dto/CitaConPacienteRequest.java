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

    /** Total de sesiones del plan — usado para fijar totalSesiones al crear tratamiento */
    private Integer totalSesionesPlan;

    /** Precio por sesión del plan */
    private java.math.BigDecimal precioPorSesion;
}
