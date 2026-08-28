package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Catálogo de productos que la clínica vende aparte de las terapias (pelotas, therabands,
 * bajalenguas, cajas de material...). Existe para que el precio salga de una lista y no de lo
 * que teclee cada recepcionista, y para poder contar unidades vendidas por producto — un cobro
 * adicional con concepto libre registra la plata pero no permite ninguna de las dos cosas.
 *
 * La venta en sí se registra como un Pago (esAdicional = true) con sus VentaItem.
 */
@Entity
@Table(name = "productos")
@Data @NoArgsConstructor @AllArgsConstructor
public class Producto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String descripcion;

    private BigDecimal precio;

    /** Contador simple: baja al vender, se repone a mano. No se permite vender más de lo que hay. */
    private Integer stock;

    /** Un producto descontinuado se desactiva en vez de borrarse — las ventas viejas lo referencian. */
    private Boolean activo;

    private LocalDateTime createdAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (activo == null) activo = true;
        if (stock == null) stock = 0;
        if (precio == null) precio = BigDecimal.ZERO;
        usuarioCreacionId = SecurityUtils.currentUserId();
    }
}
