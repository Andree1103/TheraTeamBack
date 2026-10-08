package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * El resultado de comparar el libro de movimientos con lo que el sistema tiene guardado.
 *
 * `cuadra` en true y la lista vacia es lo normal y lo que se espera a diario. Cualquier otra
 * cosa significa que algo movio dinero sin dejar su asiento — un camino nuevo, o uno viejo que
 * se nos paso al enganchar el libro.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ReconciliacionDTO {
    private boolean cuadra;
    /** La suma de la izquierda de la ecuacion: de donde sale el valor. */
    private BigDecimal origenes;
    /** La de la derecha: a donde va. Tiene que ser la misma cifra. */
    private BigDecimal destinos;
    private List<Diferencia> diferencias;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Diferencia {
        /** PACIENTE | CITA | PAQUETE | PAGO SIN ASIENTO */
        private String tipo;
        private Long id;
        private String nombre;
        private String campo;
        private BigDecimal guardado;
        private BigDecimal segunElLibro;
        /** guardado - segunElLibro. Positivo: al libro le falta anotar algo. */
        private BigDecimal diferencia;
    }
}
