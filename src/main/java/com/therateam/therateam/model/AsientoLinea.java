package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Una línea de asiento: de dónde sale un importe, o a dónde va.
 *
 * El sentido lo lleva el concepto, nunca el signo — el importe es siempre positivo. Mezclar
 * ambas cosas es como se acaba sumando lo que había que restar, y de eso venían las devoluciones
 * que contaban doble.
 */
@Entity
@Table(name = "asiento_lineas")
@Data @NoArgsConstructor @AllArgsConstructor
public class AsientoLinea {

    public enum Concepto {
        /** Entró plata al cajón. Lleva método. */
        CAJA_ENTRA,
        /** Se gastó saldo a favor del paciente. */
        CREDITO_USA,
        /** Se saldó deuda sin que entrara dinero ("Sin pago", "Paquete"). */
        CONSTANCIA,
        /** Una deuda que estaba cubierta vuelve a deberse. */
        DEUDA_LIBERA,

        /** Se cubrió deuda de una cita o un paquete. */
        DEUDA_CUBRE,
        /** Se le generó saldo a favor al paciente. */
        CREDITO_GENERA,
        /** Salió plata del cajón. Lleva método. */
        CAJA_SALE,
        /** Se retira una constancia: la deuda vuelve a estar impaga y no había dinero detrás. */
        CONSTANCIA_LIBERA;

        /** Los de la izquierda de la ecuación: de dónde sale el valor. */
        public boolean esOrigen() {
            return this == CAJA_ENTRA || this == CREDITO_USA
                || this == CONSTANCIA || this == DEUDA_LIBERA;
        }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asiento_id", nullable = false)
    private Long asientoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Concepto concepto;

    @Column(nullable = false)
    private BigDecimal importe;

    /** Solo en CAJA_ENTRA y CAJA_SALE: por dónde entró o salió. */
    @Column(name = "metodo_id")
    private Long metodoId;

    @Column(name = "cita_id")
    private Long citaId;

    @Column(name = "tratamiento_id")
    private Long tratamientoId;
}
