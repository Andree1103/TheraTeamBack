package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * Permiso granular de un Rol sobre un Módulo. Que exista esta fila ya implica acceso de
 * lectura/navegación al módulo (equivalente al viejo Set&lt;Modulo&gt; de CatRol) — crear/editar/
 * eliminar son flags independientes encima de eso, para poder dar acceso de "solo lectura".
 */
@Entity
@Table(name = "rol_modulo_permisos", uniqueConstraints = @UniqueConstraint(columnNames = {"rol_id", "modulo_id"}))
@Data @NoArgsConstructor @AllArgsConstructor
public class RolModuloPermiso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id")
    private CatRol rol;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modulo_id")
    private Modulo modulo;

    private boolean crear;
    private boolean editar;
    private boolean eliminar;
}
