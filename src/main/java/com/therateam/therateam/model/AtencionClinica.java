package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "atencion_clinica")
@Data @NoArgsConstructor @AllArgsConstructor
public class AtencionClinica {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // La cita completa no viaja al front (arrastraría toda su cadena EAGER) — solo se expone el
    // id plano vía getCitaId(), que es lo único que el listado del paciente necesita para cruzar
    // esta atención con la cita ya cargada (terapeuta, tipo de terapia, etc.).
    @JsonIgnore
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    /** Solo lectura: id de la cita, para que el front la cruce con su propio listado de citas. */
    public Long getCitaId() {
        return cita != null ? cita.getId() : null;
    }

    // Convenience de solo lectura para el front — el alta/edición sigue pasando por
    // AtencionMetricaRepository (ver AtencionClinicaService.registrar), esto no se persiste directo.
    @JsonIgnoreProperties("atencion")
    @OneToMany(mappedBy = "atencion", fetch = FetchType.EAGER)
    private List<AtencionMetrica> metricas;

    private LocalDateTime fechaInicioReal;
    private LocalDateTime fechaFinReal;
    private Integer duracionRealMin;
    private String notasPost;

    @Column(columnDefinition = "text[]")
    private String[] archivosUrl;

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
