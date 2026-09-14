package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Metadato de un archivo adjunto. El binario NO vive aca: se guarda en el disco del servidor
 * (ver ArchivoStorageService) y en la base queda solo la referencia.
 *
 * `nombreGuardado` es un uuid + extension generado por el backend. El nombre que subio el
 * usuario se conserva aparte, solo para mostrarlo y para la descarga: nunca toca el filesystem.
 */
@Entity
@Table(name = "archivos")
@Data @NoArgsConstructor @AllArgsConstructor
public class Archivo {
    /** A que se adjunta el archivo. */
    public static final String ATENCION = "ATENCION";
    public static final String HISTORIA = "HISTORIA";
    public static final String PACIENTE = "PACIENTE";
    public static final java.util.Set<String> ENTIDADES = java.util.Set.of(ATENCION, HISTORIA, PACIENTE);

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entidad_tipo")
    private String entidadTipo;

    @Column(name = "entidad_id")
    private Long entidadId;

    private String nombreOriginal;
    private String nombreGuardado;
    private String mime;
    private Long tamanoBytes;
    private String descripcion;

    private LocalDateTime createdAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;

    /** true si el navegador puede mostrarlo inline (para decidir entre miniatura y link). */
    public boolean isEsImagen() {
        return mime != null && mime.startsWith("image/");
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        usuarioCreacionId = SecurityUtils.currentUserId();
    }
}
