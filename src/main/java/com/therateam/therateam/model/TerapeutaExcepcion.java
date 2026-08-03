package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
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

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { usuarioModificacionId = SecurityUtils.currentUserId(); }
}
