package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Catálogo fijo de los módulos reales de la app (cada uno corresponde a páginas/endpoints ya
 * programados) — no es administrable desde la UI. Lo que SÍ es administrable es qué Rol tiene
 * acceso a cuáles de estos módulos (ver CatRol.modulos).
 */
@Entity
@Table(name = "modulos")
@Data @NoArgsConstructor @AllArgsConstructor
public class Modulo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key", unique = true)
    private String key;

    private String nombre;
}
