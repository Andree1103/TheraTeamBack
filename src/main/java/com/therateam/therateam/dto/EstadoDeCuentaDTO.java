package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El dinero de un paciente, movimiento a movimiento, con lo que cada sol cubrio.
 *
 * Esto es lo que antes no se podia imprimir. El historial de saldo guardaba un apunte NETO por
 * pago —si uno consumia y generaba a la vez, las dos mitades se perdian— y lo gastado en un
 * paquete apuntaba al paquete, no a la sesion. Cuando un paciente preguntaba "¿en que se fue mi
 * adelanto?", la respuesta habia que reconstruirla a mano.
 *
 * Aqui cada linea dice su concepto y a que cita o paquete fue, porque el libro lo guarda asi.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class EstadoDeCuentaDTO {
    private Long pacienteId;
    private String paciente;
    /** Lo que el sistema tiene guardado hoy. */
    private BigDecimal saldoGuardado;
    /** Lo que sale de sumar el libro. Si no coinciden, falta un asiento. */
    private BigDecimal saldoSegunElLibro;
    private List<Movimiento> movimientos;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Movimiento {
        private Long asientoId;
        private String tipo;
        private LocalDateTime fecha;
        private String nota;
        private List<Linea> lineas;
        /** El saldo del paciente despues de este asiento, segun el libro. */
        private BigDecimal saldoDespues;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Linea {
        private String concepto;
        private BigDecimal importe;
        private String metodo;
        private Long citaId;
        private Long tratamientoId;
        /** En castellano, para poder enseñarselo a un paciente sin traducir nada. */
        private String enCristiano;
    }
}
