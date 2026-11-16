package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cobrar una cuenta de crédito: lo del mes, de una vez.
 *
 * El caso real es "Rocío paga a fin de mes": una sola conversación que salda cinco citas y un
 * paquete. Obligar a cobrarlos uno por uno es lo que hace que la gente no lo haga.
 */
@Data @NoArgsConstructor
public class CobroCreditoRequest {

    /**
     * Cómo paga. Obligatorio.
     *
     * Aquí va el descuento de planilla: es un método de pago más, marcado como que NO es dinero
     * en caja, así que el pago se registra, la deuda se salda y el arqueo del cierre no lo suma
     * porque no entró plata al cajón. No hay un segundo camino para el descuento de sueldo —
     * es el mismo, con otro método.
     */
    private Long metodoId;

    /**
     * Qué se cobra. Si no viene, se cobra todo lo pendiente de la cuenta.
     *
     * Sirve para cuando el titular paga solo parte: "estas tres citas sí, el paquete el mes que
     * viene".
     */
    private List<Linea> lineas;

    /**
     * Cuánto paga. Si no viene, se cobra la deuda completa de las líneas elegidas.
     *
     * Si es menos que el total, se aplica de lo más viejo a lo más nuevo, que es el orden en que
     * se cobra: lo que lleva más tiempo sin pagarse se salda primero. Lo que no alcance queda
     * pendiente.
     */
    private BigDecimal monto;

    private String referencia;
    private String notas;

    /** Una línea de la cuenta: {@code tipo} es CITA o PAQUETE, como los devuelve /lineas. */
    @Data @NoArgsConstructor
    public static class Linea {
        private String tipo;
        private Long id;
    }
}
