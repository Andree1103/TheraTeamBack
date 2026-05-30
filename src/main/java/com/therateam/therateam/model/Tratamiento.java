package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tratamientos")
@Data @NoArgsConstructor @AllArgsConstructor
public class Tratamiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapia_id")
    private TipoTerapia tipoTerapia;

    private String nombre;
    private Integer totalSesiones;
    private BigDecimal precioPorSesion;

    // GENERATED ALWAYS AS — solo lectura
    @Column(insertable = false, updatable = false)
    private BigDecimal montoTotal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id")
    private CatEstadoTratamiento estado;

    private Integer sesionesAtendidas;

    // GENERATED ALWAYS AS — solo lectura
    @Column(name = "sesiones_pendientes", insertable = false, updatable = false)
    private Integer sesionesPendientes;

    private BigDecimal totalCobrado;

    @Column(name = "saldo_a_favor")
    private BigDecimal saldoAFavor;
    private LocalDate fechaInicio;
    private String notas;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (sesionesAtendidas == null) sesionesAtendidas = 0;
        if (totalCobrado == null) totalCobrado = BigDecimal.ZERO;
        if (saldoAFavor == null) saldoAFavor = BigDecimal.ZERO;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
