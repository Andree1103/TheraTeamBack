package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tipos_terapia")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TipoTerapia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Column(name = "key")
    private String key;

    private Integer duracionMinutos;
    private Integer maxPacientes;
    private Boolean activo;

    @ManyToOne
    @JoinColumn(name = "area_id")
    private CatArea area;

    /** Especialidad dentro del área (ej. Psicología, Ocupacional, Traumatología). */
    private String especialidad;

    /** Sesiones sugeridas para completar esta terapia/evaluación (ej. 6 sesiones para Descarte de TDAH). */
    private Integer sesionesSugeridas;

    /** Notas/reglas adicionales (ej. "Niños menores de 18 meses pasan por una evaluación previa"). */
    @Column(columnDefinition = "TEXT")
    private String comentario;
}
