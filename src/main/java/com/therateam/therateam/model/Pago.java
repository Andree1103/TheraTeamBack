package com.therateam.therateam.model;

import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "metodo_id")
    private CatMetodoPago metodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    private BigDecimal montoRecibido;
    private BigDecimal montoAplicado;
    private BigDecimal saldoGenerado;
    private BigDecimal saldoPrevio;
    private String referencia;
    private String notas;
    private LocalDateTime fechaPago;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (fechaPago == null) fechaPago = LocalDateTime.now();
        if (saldoGenerado == null) saldoGenerado = BigDecimal.ZERO;
        if (saldoPrevio == null) saldoPrevio = BigDecimal.ZERO;
    }
}
