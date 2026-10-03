package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cat_metodos_pago")
@Data @NoArgsConstructor @AllArgsConstructor
public class CatMetodoPago {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "key") private String key;
    private String nombre;
    private Boolean activo;

    /**
     * false = los pagos con este metodo no suman al arqueo del cierre de caja.
     *
     * Para los metodos que no son dinero que entra al cajon ese dia: "Sin pago", que deja
     * constancia de una sesion cobrada semanas antes, o un seguro que liquida aparte. El pago
     * sigue existiendo y la cita sigue saldada; solo se saca del total y se muestra por separado,
     * para que el cierre cuadre contra el efectivo real.
     */
    @Column(name = "cuenta_en_caja")
    private Boolean cuentaEnCaja;

    /** Ante un nulo (fila vieja, o una creada sin pasar por aqui) se asume que SI es dinero. */
    public boolean cuentaEnCajaOEsDinero() { return !Boolean.FALSE.equals(cuentaEnCaja); }
}
