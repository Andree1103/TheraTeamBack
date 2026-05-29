package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "citas")
@Data @NoArgsConstructor @AllArgsConstructor
public class Cita {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sesion_id")
    private Sesion sesion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modalidad_id")
    private CatModalidad modalidad;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Integer duracionMinutos;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id")
    private CatEstadoCita estado;

    private String linkVideollamada;
    private String notasPrevias;
    private Boolean recordatorioEnviado;

    // Autorreferencia: apunta a la cita anterior si es reprogramación
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reprogramacion_de")
    private Cita reprogramacionDe;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (recordatorioEnviado == null) recordatorioEnviado = false;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
