package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cat_monedas")
@Data @NoArgsConstructor @AllArgsConstructor
public class CatMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    private String simbolo;
    private String nombre;
    private Boolean activo;
}
