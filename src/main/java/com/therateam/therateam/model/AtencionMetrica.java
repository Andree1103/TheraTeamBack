package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "atencion_metricas")
@Data @NoArgsConstructor @AllArgsConstructor
public class AtencionMetrica {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "atencion_id")
    private AtencionClinica atencion;

    private String metrica;
    private BigDecimal valor;
    private String unidad;
    private String notas;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
