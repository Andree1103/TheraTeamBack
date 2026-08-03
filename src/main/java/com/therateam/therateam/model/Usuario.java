package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data @NoArgsConstructor @AllArgsConstructor
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String apellido;
    private String email;

    /**
     * Al recibir un usuario del cliente, este campo trae la contraseña en texto plano (nunca un
     * hash) — UsuarioService la encripta antes de persistir. WRITE_ONLY evita que el hash salga
     * jamás en una respuesta JSON.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id")
    private CatRol rol;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sede_id")
    private Sede sede;

    private Boolean activo;

    /** Si es true, este usuario (terapeuta) solo ve sus propias citas, nunca las de otros. */
    private Boolean citasSoloPropias;

    /** Si es false, este usuario puede ver citas pero no crear ninguna (solo lectura/registro de atención). */
    private Boolean citasPuedeCrear;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }
}
