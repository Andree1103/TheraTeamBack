package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Una línea de la venta: qué producto, cuántos y a qué precio. Cuelga del Pago que registró la
 * plata (esAdicional = true).
 *
 * Todo plano (ids sueltos, sin @ManyToOne) a propósito: estos items se serializan dentro del
 * detalle del pago fuera de la transacción, y una relación lazy ahí revienta con
 * LazyInitializationException — mismo problema que ya obligó a proyectar SaldoMovimiento a DTO.
 *
 * El nombre y el precio unitario se copian del producto al vender y nunca se recalculan: si
 * mañana sube el precio de la pelota o le cambian el nombre, la venta vieja debe seguir
 * diciendo lo que realmente se cobró ese día.
 */
@Entity
@Table(name = "venta_items")
@Data @NoArgsConstructor @AllArgsConstructor
public class VentaItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pago_id")
    private Long pagoId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "nombre_producto")
    private String nombreProducto;

    private Integer cantidad;

    private BigDecimal precioUnitario;

    private BigDecimal subtotal;
}
