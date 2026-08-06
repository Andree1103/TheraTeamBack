package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CajaResumenDTO {

    private LocalDate fecha;
    private Integer turno;
    /** Hora de corte configurada (HH:mm) usada para separar el turno 1 del turno 2 este día. */
    private String horaCorte;
    private BigDecimal saldoInicial;
    private List<IngresoMetodo> ingresosPorMetodo;
    private BigDecimal totalIngresos;
    private BigDecimal egresos;
    private String comentario;
    private BigDecimal saldoFinal;
    private boolean cerrado;
    private String cerradoPorNombre;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngresoMetodo {
        private Long metodoId;
        private String metodoNombre;
        private BigDecimal monto;
    }
}
