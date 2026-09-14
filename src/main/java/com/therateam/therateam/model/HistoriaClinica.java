package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La ficha clinica de un paciente para una plantilla dada.
 *
 * Los valores viven en un JSONB con la forma {claveDelCampo: valor} en vez de una tabla
 * campo-valor: la plantilla ya define la estructura, y asi agregar un campo nuevo no obliga a
 * migrar ninguna fila existente.
 *
 * Un paciente puede tener varias historias si se atiende en areas distintas (una por plantilla).
 */
@Entity
@Table(name = "historias_clinicas")
@Data @NoArgsConstructor @AllArgsConstructor
public class HistoriaClinica {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    /** Solo lectura para el front, que ya tiene el paciente cargado por su lado. */
    public Long getPacienteId() { return paciente != null ? paciente.getId() : null; }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plantilla_id")
    private HcPlantilla plantilla;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos", columnDefinition = "jsonb")
    private Map<String, Object> datos = new LinkedHashMap<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (datos == null) datos = new LinkedHashMap<>();
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }
}
