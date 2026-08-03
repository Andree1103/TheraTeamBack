package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Catálogo de paquetes predefinidos (ej. "Paquete 5 sesiones terapia física") que el admin
 * mantiene desde Configuraciones — sirve como plantilla para autocompletar nombre/sesiones/precio
 * al crear un paquete real (Tratamiento) para un paciente. No obliga: los campos siguen editables.
 */
@Entity
@Table(name = "plantillas_paquete")
@Data @NoArgsConstructor @AllArgsConstructor
public class PlantillaPaquete {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    /** Etiqueta libre de agrupación (ej. "Paquete Terapia Física") — no es un área ni un tipo de terapia. */
    private String categoria;

    private Integer totalSesiones;

    private BigDecimal precioTotal;

    private Boolean activo;
}
