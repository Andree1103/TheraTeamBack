package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Grupo de campos dentro de una plantilla (ej. "Antecedentes", "Evaluacion inicial"). */
@Entity
@Table(name = "hc_secciones")
@Data @NoArgsConstructor @AllArgsConstructor
public class HcSeccion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id")
    private HcPlantilla plantilla;

    private String nombre;
    private Integer orden;

    @OneToMany(mappedBy = "seccion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orden ASC, id ASC")
    private List<HcCampo> campos = new ArrayList<>();

    @PrePersist
    void onCreate() { if (orden == null) orden = 0; }
}
