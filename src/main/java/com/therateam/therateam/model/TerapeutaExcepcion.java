package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "terapeuta_excepciones")
@Data @NoArgsConstructor @AllArgsConstructor
public class TerapeutaExcepcion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    private LocalDate fecha;

    // BLOQUEO_TOTAL | BLOQUEO_PARCIAL | EXTRA
    private String tipo;

    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String motivo;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
