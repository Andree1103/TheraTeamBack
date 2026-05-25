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
}
