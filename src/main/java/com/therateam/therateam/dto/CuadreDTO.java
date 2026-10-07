package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * El resultado de recalcular el dinero desde los pagos y compararlo con lo que hay guardado.
 *
 * La misma cifra vive en cuatro sitios —monto_pagado de la cita, su estado de pago, el saldo
 * del paciente y el total cobrado del paquete— y todos se pueden deducir de los pagos. Cada
 * camino que mueve dinero tiene que acordarse de actualizar los cuatro; cuando uno se olvida,
 * la diferencia se queda ahi callada hasta que alguien cuadra la caja. Esto la saca a la luz.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CuadreDTO {
    private boolean corregido;
    private List<Diferencia> diferencias;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Diferencia {
        /** CITA | PACIENTE | PAQUETE */
        private String tipo;
        private Long id;
        private String nombre;
        private String campo;
        private BigDecimal guardado;
        private BigDecimal calculado;
        /**
         * Si se puede arreglar solo.
         *
         * Hay diferencias que el sistema puede rehacer sin riesgo porque la fuente es clara (lo
         * aplicado por los pagos de una cita). Y hay otras donde el calculo es una conjetura: un
         * paciente con saldo y SIN ningun movimiento que lo respalde podria ser un dato corrupto
         * o un ajuste hecho a mano en la base. Ponerlo a cero a ciegas seria peor que el fallo,
         * asi que se avisa y se deja decidir a una persona.
         */
        private boolean corregible;
        private String nota;
    }
}
