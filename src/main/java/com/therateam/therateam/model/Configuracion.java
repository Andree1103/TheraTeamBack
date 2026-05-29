package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion")
@Data @NoArgsConstructor @AllArgsConstructor
public class Configuracion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sede_id")
    private Sede sede;

    private String clave;
    private String valor;
    private String descripcion;
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
