package com.therateam.therateam.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CerrarCajaRequest {
    private LocalDate fecha;
    private Integer turno;
    private BigDecimal egresos;
    private String comentario;
}
