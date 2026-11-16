package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Un paciente que puede cargarse a una cuenta de crédito.
 *
 * Varios por cuenta: el terapeuta que trae a su madre, su padre y su hijo tiene tres filas aquí y
 * una sola deuda. Esto es lo que contesta la pregunta de la clínica, "a quién está amarrado este
 * crédito": se lee de una fila, no se reconstruye.
 *
 * Un paciente solo puede estar ACTIVO en una cuenta a la vez (índice parcial
 * uq_ccp_un_paciente_una_cuenta). Si estuviera en dos, al marcar la cita habría que preguntar a
 * cuál se carga y el reporte tendría dos respuestas.
 *
 * DESAMARRAR NO BORRA, DESACTIVA. Las citas ya cargadas apuntan a este par con una FK compuesta
 * (fk_citas_credito_cubre_al_paciente), así que borrar la fila falla mientras exista historia — y
 * si se pudiera borrar se perdería el dato de quién respondía cuando se atendió. Desamarrar
 * significa "no cargues más citas de este paciente aquí", no "esto nunca pasó".
 *
 * Con id propio y no clave compuesta, como {@link RolModuloPermiso}: en el proyecto no hay
 * ninguna entidad con clave compuesta y mapear una obligaría a una @IdClass aparte para no ganar
 * nada. La unicidad del par la garantizan las restricciones de la base igual.
 */
@Entity
@Table(name = "cuenta_credito_pacientes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cuenta_id", "paciente_id"}))
@Data @NoArgsConstructor @AllArgsConstructor
public class CuentaCreditoPaciente {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id")
    private CuentaCredito cuenta;

    @NotNull(message = "El paciente es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    /** false = amarre histórico: no se le cargan citas nuevas, pero las que ya se cargaron siguen. */
    private Boolean activo;

    private LocalDateTime createdAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (activo == null) activo = true;
        usuarioCreacionId = SecurityUtils.currentUserId();
    }

    /** Ante un nulo (fila creada sin pasar por aquí) se asume activo. */
    public boolean estaActivo() { return !Boolean.FALSE.equals(activo); }
}
