package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila del detalle "qué se cobró aparte" en el cierre de caja.
 *
 * Los productos ya tenían su desglose; los cobros adicionales sin producto detrás —una copia de
 * informe, un material, una consulta extra— solo aparecían sumados en la fila "Otros cobros".
 * Al cuadrar el turno eso obliga a salir a Pagos a buscar de qué eran, que es justo lo que no
 * se puede hacer con el cajón abierto.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CobroAdicionalDTO {
    private Long pagoId;
    private LocalDateTime fecha;
    private String paciente;
    private String concepto;
    private String metodo;
    private BigDecimal monto;
}
