package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cat_estados_sesion")
@Data @NoArgsConstructor @AllArgsConstructor
public class CatEstadoSesion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "key") private String key;
    private String nombre;
    private String colorHex;
    private Boolean activo;
}
