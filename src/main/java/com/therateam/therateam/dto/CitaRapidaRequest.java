package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CitaRapidaRequest {

    // Obligatorios para vincular al paciente
    private Long pacienteId;
    private Long terapeutaId;
    private Long tipoTerapiaId;

    // Opcional: si se envía, se reutiliza ese tratamiento en vez de buscar/crear uno
    private Long tratamientoId;

    // Opcionales para nuevo tratamiento (se ignoran si tratamientoId está presente)
    private Integer totalSesiones;
    private BigDecimal precioPorSesion;
    private String nombreTratamiento;

    // Campos de la cita
    private Long modalidadId;
    private Long estadoCitaId;   // estado de la cita (ej: PROGRAMADA)
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Integer duracionMinutos;
    private String notasPrevias;
    private String linkVideollamada;
}
