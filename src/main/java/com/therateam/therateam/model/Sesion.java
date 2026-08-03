package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones")
@Data @NoArgsConstructor @AllArgsConstructor
public class Sesion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tratamiento_id")
    private Tratamiento tratamiento;

    private Integer numero;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id")
    private CatEstadoSesion estado;

    // FK diferida — apunta a la cita activa (puede ser null al crear)
    @JsonIgnoreProperties({"sesion", "reprogramacionDe"})
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cita_activa_id")
    private Cita citaActiva;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }
}
