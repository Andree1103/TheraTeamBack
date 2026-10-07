package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data @NoArgsConstructor @AllArgsConstructor
public class Pago {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tratamiento_id")
    private Tratamiento tratamiento;

    @NotNull(message = "El paciente es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    /**
     * Como entro el dinero. Puede ir vacio SOLO cuando no entro ninguno (ver
     * {@link #isMetodoPresenteCuandoEntraDinero()}): pagar con el saldo a favor no es un medio de
     * pago — ese dinero ya entro el dia del adelanto, con el medio que tuviera entonces, y
     * obligar a elegir uno aqui lo contaria dos veces en la caja.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "metodo_id")
    private CatMetodoPago metodo;

    /**
     * Si este cobro movio dinero de verdad. Se fija al crearlo y NO cambia.
     *
     * Antes la pregunta se le hacia al metodo: cat_metodos_pago.cuenta_en_caja. Eso es un dato
     * de HOY aplicado al PASADO — si alguien marca manana "Efectivo" como que no cuenta, todos
     * los arqueos y todas las reversiones de meses anteriores cambian de significado en
     * silencio. El arqueo y las reversiones leen esto; el catalogo solo decide el valor inicial
     * de los pagos nuevos.
     */
    @Column(name = "trajo_dinero")
    private Boolean trajoDinero;

    /** Ante un nulo (fila anterior a la migracion) manda el metodo, como se hacia antes. */
    /**
     * Lo que el paciente puso de su bolsillo en este cobro, venga de donde venga.
     *
     * Son dos cosas distintas y las dos son suyas: el efectivo que entrego ahora, y el saldo a
     * favor que se le consumio — que era dinero que habia entregado antes. Si se anula el cobro,
     * tiene derecho a recuperar la suma de ambos.
     *
     * No es lo mismo que trajoDineroDeVerdad(), que responde "¿entro efectivo a la caja HOY?".
     * Confundirlas costo un fallo: un cobro pagado con el saldo del paciente responde NO a esa
     * pregunta, asi que al anularlo no se le devolvia nada y el dinero se evaporaba. La pregunta
     * buena no es si la caja lo vio, es si el paciente lo puso.
     *
     * Un cobro con "Sin pago" o "Paquete" sigue dando cero: ahi no puso nada, solo se dejo
     * constancia.
     */
    public BigDecimal loQuePusoElPaciente() {
        BigDecimal enEfectivo = trajoDineroDeVerdad() && montoRecibido != null
                ? montoRecibido.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal deSuSaldo = (saldoPrevio != null ? saldoPrevio : BigDecimal.ZERO)
                .subtract(saldoGenerado != null ? saldoGenerado : BigDecimal.ZERO)
                .max(BigDecimal.ZERO);
        return enEfectivo.add(deSuSaldo);
    }

    /** Solo la parte que entro como dinero contante: lo unico que se puede devolver en mano. */
    public BigDecimal elEfectivoQueEntro() {
        return trajoDineroDeVerdad() && montoRecibido != null
                ? montoRecibido.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    public boolean trajoDineroDeVerdad() {
        if (trajoDinero != null) return trajoDinero;
        return metodo != null && metodo.cuentaEnCajaOEsDinero();
    }

    /**
     * El dinero no entra manana.
     *
     * "Fecha de pago" se puede escribir a mano —hace falta, para registrar un cobro de ayer que
     * no se alcanzo a cargar— pero nada impedia teclear una fecha futura. Paso: un cobro del 1
     * de octubre quedo fechado el 8, y esos S/ 60 no aparecian en el arqueo de ningun dia hasta
     * entonces. Dinero real, invisible durante una semana.
     *
     * Se deja un margen de unos minutos por si el reloj del navegador va algo adelantado
     * respecto al del servidor; lo que se rechaza es la fecha escrita a mano, no el desfase.
     */
    @AssertTrue(message = "La fecha de pago no puede ser futura")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFechaDePagoNoFutura() {
        return fechaPago == null || !fechaPago.isAfter(LocalDateTime.now().plusMinutes(10));
    }

    @AssertTrue(message = "El método de pago es obligatorio")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isMetodoPresenteCuandoEntraDinero() {
        if (montoRecibido == null || montoRecibido.compareTo(BigDecimal.ZERO) <= 0) return true;
        return metodo != null;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    // 0 es válido: cubrir la deuda solo con el saldo a favor del paciente, sin dinero nuevo.
    @NotNull(message = "El monto recibido es obligatorio")
    @DecimalMin(value = "0.0", message = "El monto recibido no puede ser negativo")
    private BigDecimal montoRecibido;
    private BigDecimal montoAplicado;
    private BigDecimal saldoGenerado;
    private BigDecimal saldoPrevio;

    /**
     * Cuanto del saldo a favor se quiere usar en ESTE cobro. No se guarda: es una instruccion.
     *
     * Lo que se guarda es el resultado (saldoPrevio y saldoGenerado), que ya dice cuanto se
     * consumio. Esto es lo que pide quien cobra, y hacia falta porque antes no habia forma de
     * pedir "cobrale 45 y no le toques los 190 que tiene": el motor los gastaba igual.
     *
     * null = el de siempre, todo el saldo disponible. 0 = no usar nada.
     */
    @Transient
    private BigDecimal saldoAAplicar;

    private String referencia;
    private String notas;
    private LocalDateTime fechaPago;

    /** Concepto libre para cobros adicionales (ej. "Material adicional", "Consulta extra"). */
    private String concepto;
    /** true = cobro adicional: ingreso aparte que no descuenta ninguna deuda ni genera saldo a favor. */
    @Column(name = "es_adicional")
    private Boolean esAdicional = false;

    /** true = este registro es la devolución de otro pago (no un cobro) — para auditoría, nunca
     *  se borra el pago original: se revierte su efecto y se deja este registro como evidencia. */
    @Column(name = "es_devolucion")
    private Boolean esDevolucion = false;
    /** Id del Pago que este registro devuelve — solo se llena cuando esDevolucion = true. */
    @Column(name = "pago_origen_id")
    private Long pagoOrigenId;

    /**
     * Solo de entrada/salida: las lineas de productos de una venta ({productoId, cantidad}).
     * No se persiste aca — cada linea vive en venta_items apuntando a este pago. El precio se
     * toma del catalogo al vender, nunca de lo que mande el cliente.
     */
    @Transient
    private java.util.List<VentaItem> items;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    private LocalDateTime createdAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        // Se congela aqui: el metodo decide, pero solo en este momento.
        if (trajoDinero == null) trajoDinero = metodo == null ? false : metodo.cuentaEnCajaOEsDinero();
        if (fechaPago == null) fechaPago = LocalDateTime.now();
        if (saldoGenerado == null) saldoGenerado = BigDecimal.ZERO;
        if (saldoPrevio == null) saldoPrevio = BigDecimal.ZERO;
        if (esAdicional == null) esAdicional = false;
        if (esDevolucion == null) esDevolucion = false;
        usuarioCreacionId = SecurityUtils.currentUserId();
    }
}
