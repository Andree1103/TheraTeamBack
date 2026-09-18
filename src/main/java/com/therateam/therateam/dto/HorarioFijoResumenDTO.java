package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Una línea de horario fijo vista desde fuera de la ficha del paciente: para el listado
 * general y su exportación.
 *
 * La entidad no sirve aquí porque oculta el paciente a propósito (@JsonIgnore) — desde su
 * propia ficha ya se sabe de quién es. En la vista global el paciente es justo la columna que
 * se necesita, así que se aplana lo mínimo: quién, con quién, cuándo y de qué terapia.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioFijoResumenDTO {
    private Long id;
    private Long pacienteId;
    private String paciente;
    private String dni;
    private String sede;
    private Long terapeutaId;
    private String terapeuta;
    private Long tipoTerapiaId;
    private String tipoTerapia;
    /** 1 = lunes … 7 = domingo. */
    private Integer diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String notas;
}
