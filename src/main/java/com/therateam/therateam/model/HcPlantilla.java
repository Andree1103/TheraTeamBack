package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla de historia clinica: define QUE campos tiene la ficha, no los valores.
 *
 * Es configurable por area (Fisica, Kids, Psicologia...) porque cada disciplina pregunta cosas
 * distintas. area_id nulo = la plantilla sirve para cualquier area.
 */
@Entity
@Table(name = "hc_plantillas")
@Data @NoArgsConstructor @AllArgsConstructor
public class HcPlantilla {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Para que ficha sirve la plantilla. */
    public static final String HISTORIA = "HISTORIA";
    public static final String ATENCION = "ATENCION";
    public static final java.util.Set<String> TIPOS_FICHA = java.util.Set.of(HISTORIA, ATENCION);

    private String nombre;

    /** HISTORIA (ficha del paciente) o ATENCION (nota de la sesion). */
    private String tipo;

    /**
     * Tipo de terapia al que aplica. Nulo = generica, sirve para cualquiera.
     *
     * Se ata al tipo de terapia y no al area porque el area es demasiado gruesa (una
     * Evaluacion Psicologica y una Terapia de Lenguaje son las dos de Kids y no preguntan lo
     * mismo) y porque es lo que ya lleva la cita: asi la plantilla de una atencion se resuelve
     * sola, sin que el terapeuta tenga que elegirla.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapia_id")
    private TipoTerapia tipoTerapia;

    private String descripcion;
    private Boolean activo;
    private Integer orden;

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orden ASC, id ASC")
    private List<HcSeccion> secciones = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (activo == null) activo = true;
        if (orden == null) orden = 0;
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }
}
