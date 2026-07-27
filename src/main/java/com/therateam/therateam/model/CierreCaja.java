package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cierres_caja")
@Data @NoArgsConstructor @AllArgsConstructor
public class CierreCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private LocalDate fecha;

    private BigDecimal saldoInicial;
    private BigDecimal totalIngresos;
    private BigDecimal egresos;
    private BigDecimal saldoFinal;
    private String comentario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cerrado_por")
    private Usuario cerradoPor;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (egresos == null) egresos = BigDecimal.ZERO;
        if (saldoInicial == null) saldoInicial = BigDecimal.ZERO;
        if (totalIngresos == null) totalIngresos = BigDecimal.ZERO;
        if (saldoFinal == null) saldoFinal = BigDecimal.ZERO;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
