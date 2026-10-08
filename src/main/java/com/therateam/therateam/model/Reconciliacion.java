package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** El resultado de una pasada del reconciliador, guardado para poder mirar la racha. */
@Entity
@Table(name = "reconciliaciones")
@Data @NoArgsConstructor @AllArgsConstructor
public class Reconciliacion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime momento;

    /** false si la lanzo una persona desde la pantalla, en vez del proceso de la noche. */
    private Boolean automatica;

    private Boolean cuadra;
    private Integer diferencias;
    private BigDecimal origenes;
    private BigDecimal destinos;

    /** Las diferencias en JSON. Solo se lee cuando algo falla, asi que no merece tabla propia. */
    @Column(columnDefinition = "text")
    private String detalle;

    @PrePersist
    void onCreate() { if (momento == null) momento = LocalDateTime.now(); }
}
