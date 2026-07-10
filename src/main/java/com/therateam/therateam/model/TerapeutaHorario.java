package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "terapeuta_horario")
@Data @NoArgsConstructor @AllArgsConstructor
public class TerapeutaHorario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    // 1 (lunes) .. 7 (domingo)
    private Integer diaSemana;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turno_id")
    private CatTurno turno;

    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Boolean activo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (activo == null) activo = true;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
