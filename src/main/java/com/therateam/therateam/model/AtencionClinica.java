package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "atencion_clinica")
@Data @NoArgsConstructor @AllArgsConstructor
public class AtencionClinica {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    private LocalDateTime fechaInicioReal;
    private LocalDateTime fechaFinReal;
    private Integer duracionRealMin;
    private String notasPost;

    @Column(columnDefinition = "text[]")
    private String[] archivosUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
