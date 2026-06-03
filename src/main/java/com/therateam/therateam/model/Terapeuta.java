package com.therateam.therateam.model;

import org.hibernate.annotations.BatchSize;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "terapeutas")
@Data @NoArgsConstructor @AllArgsConstructor
public class Terapeuta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapeuta_id")
    private CatTipoTerapeuta tipoTerapeuta;

    private String cmp;
    private String telefono;
    private String fotoUrl;
    private String horarioDescripcion;
    private Boolean activo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @BatchSize(size = 30)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "terapeuta_especialidades",
        joinColumns = @JoinColumn(name = "terapeuta_id"),
        inverseJoinColumns = @JoinColumn(name = "especialidad_id")
    )
    private List<CatEspecialidad> especialidades = new ArrayList<>();

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
