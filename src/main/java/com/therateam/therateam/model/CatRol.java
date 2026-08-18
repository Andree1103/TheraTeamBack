package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cat_roles")
@Data @NoArgsConstructor @AllArgsConstructor
public class CatRol {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "key") private String key;
    private String nombre;
    private Boolean activo;

    /** Dato sensible: si es true, los usuarios con este rol ven el celular de los pacientes —
     *  false por defecto, se activa por rol desde Seguridad > Roles (no es un permiso por módulo). */
    @Column(name = "pacientes_ver_telefono")
    private Boolean pacientesVerTelefono;

    /**
     * Un permiso por módulo al que este rol tiene acceso — que exista la fila ya da acceso de
     * lectura/navegación; crear/editar/eliminar son flags independientes (las lecturas de datos
     * de referencia cross-módulo siguen abiertas a cualquier autenticado, ver SecurityConfig).
     */
    @OneToMany(mappedBy = "rol", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RolModuloPermiso> permisos = new ArrayList<>();
}
