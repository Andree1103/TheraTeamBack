package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Una fila del reporte "qué se vendió": producto, unidades y plata en el rango consultado. */
@Data @NoArgsConstructor @AllArgsConstructor
public class VentaResumenDTO {
    private Long productoId;
    private String nombreProducto;
    private Long unidades;
    private BigDecimal total;
}
