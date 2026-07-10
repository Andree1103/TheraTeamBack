package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cat_areas")
@Data @NoArgsConstructor @AllArgsConstructor
public class CatArea {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "key") private String key;
    private String nombre;
    private Boolean activo;
}
