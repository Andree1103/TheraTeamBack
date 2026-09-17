package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * El horario habitual de un paciente: "viene los lunes a las 9 con Carla, terapia KIDS".
 *
 * Es una anotación de REFERENCIA. No reserva el espacio en la agenda ni genera citas: agendar
 * sigue siendo manual. Sirve para responder de un vistazo si el paciente tiene horarios fijos,
 * en qué terapias y con qué terapeutas.
 */
@Entity
@Table(name = "paciente_horario_fijo")
@Data @NoArgsConstructor @AllArgsConstructor
public class PacienteHorarioFijo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El paciente no se serializa: estos horarios se piden siempre desde su propia ficha, y
    // devolverlo entero arrastraría su usuario, su sede y su saldo en cada fila.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    /** Opcional: se puede anotar el horario sin precisar todavía qué terapia. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapia_id")
    private TipoTerapia tipoTerapia;

    /** 1 = lunes … 7 = domingo, igual que en terapeuta_horario. */
    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    /** Puede quedar en null si solo se sabe la hora de entrada. */
    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(length = 255)
    private String notas;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;

    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    /** El id del paciente sin arrastrar la entidad — lo consume el front. */
    @Transient
    public Long getPacienteId() {
        return paciente != null ? paciente.getId() : null;
    }

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        createdAt = ahora;
        updatedAt = ahora;
        if (activo == null) activo = true;
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
