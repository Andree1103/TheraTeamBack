package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadDiaDTO {
    private LocalDate fecha;
    private Integer diaSemana; // 1 (lunes) .. 7 (domingo)
    private List<Franja> franjas;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Franja {
        private LocalTime horaInicio;
        private LocalTime horaFin;
    }
}
