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
    /**
     * El mismo dinero visto por concepto (terapias / productos / otros cobros) en vez de por
     * método de pago. Suma exactamente lo mismo que ingresosPorMetodo: son dos cortes del
     * mismo total, no dos totales distintos.
     */
    private List<IngresoConcepto> ingresosPorConcepto;
    /** Qué productos se vendieron en este turno — el detalle detrás de la fila "Productos". */
    private List<VentaResumenDTO> ventasPorProducto;
    /**
     * Cobros del turno con un metodo marcado como "no es dinero en caja" (ej. "Sin pago").
     *
     * NO entran en totalIngresos ni en el saldo final: de eso no hay nada en el cajon. Se
     * mandan igual para que el cierre pueda mostrarlos aparte — si no se vieran, la pregunta
     * "¿y lo que registre hoy?" no tendria respuesta en la pantalla donde se hace el arqueo.
     */
    private List<IngresoMetodo> ingresosFueraDeCaja;
    private BigDecimal totalFueraDeCaja;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngresoConcepto {
        /** TERAPIAS | PRODUCTOS | OTROS — para que el front no dependa del texto mostrado. */
        private String clave;
        private String nombre;
        private BigDecimal monto;
    }
}
