package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un movimiento del saldo a favor de un paciente.
 *
 * El saldo vive como un total en pacientes.saldo_a_favor; esta tabla es su historial, para
 * poder responder "¿por qué este paciente tiene saldo?" y "¿de qué terapeuta salió?".
 */
@Entity
@Table(name = "saldo_movimientos")
@Data
@NoArgsConstructor
public class SaldoMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    /** Con signo: positivo suma saldo, negativo lo consume. */
    @Column(nullable = false)
    private BigDecimal monto;

    /** Saldo del paciente después de aplicar este movimiento. */
    @Column(name = "saldo_resultante", nullable = false)
    private BigDecimal saldoResultante;

    @Column(nullable = false, length = 255)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id")
    private Pago pago;

    /** Copiado de la cita al momento del movimiento: si luego le cambian de terapeuta,
     *  el histórico debe seguir mostrando quién atendía cuando se generó el saldo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(name = "usuario_creacion_id")
    private Long usuarioCreacionId;

    @PrePersist
    void alGuardar() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
