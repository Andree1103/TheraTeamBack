package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** Una línea del horario fijo tal como la manda el front: solo ids, sin entidades anidadas. */
@Data
@NoArgsConstructor
public class HorarioFijoRequest {
    private Long terapeutaId;
    /** Opcional: se puede anotar el horario sin precisar todavía qué terapia. */
    private Long tipoTerapiaId;
    /** 1 = lunes … 7 = domingo. */
    private Integer diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String notas;
}
