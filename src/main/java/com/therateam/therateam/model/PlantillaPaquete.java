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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "especialidad_id")
    private CatEspecialidad especialidad;

    /**
     * Área a la que pertenece este paquete (ej. "Kids" vs "Física") — permite tener plantillas
     * con el mismo nombre/sesiones que solo se diferencian por área. Al elegir la plantilla en
     * "Nuevo paquete" filtra los tipos de terapia y terapeutas disponibles, igual que en Citas.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id")
    private CatArea area;

    /**
     * Tipo de terapia al que corresponde este paquete — al elegir la plantilla en "Nuevo paquete"
     * (Tratamiento), esto autocompleta el tipo de terapia y, por lo tanto, el área que filtra
     * qué terapeutas pueden atenderlo. Opcional: si queda sin asignar, el usuario lo elige a mano.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapia_id")
    private TipoTerapia tipoTerapia;

    private Integer totalSesiones;

    private BigDecimal precioTotal;

    private Boolean activo;
}
