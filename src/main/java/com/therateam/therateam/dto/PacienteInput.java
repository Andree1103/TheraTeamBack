package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class PacienteInput {
    private String dni;
    private String nombre;
    private String apellido;
    private String telefono;
    private String correo;
    /** Necesaria para saber si es menor: si viene, se exigen los datos del apoderado. */
    private LocalDate fechaNacimiento;
    private String dniApoderado;
    private String nombreApoderado;
    private String celularApoderado;

    /**
     * Tipo de terapia PROPIO de este acompañante, cuando comparte horario con otro paciente.
     *
     * Dos pacientes en el mismo bloque del mismo terapeuta no tienen por qué recibir lo mismo:
     * en física, uno puede venir a descarga muscular y el otro a convencional. Antes el backend
     * le imponía a todos el tipo de la cita principal, y en pantalla ni siquiera se podía elegir.
     *
     * Opcional: si no viene, se usa el de la cita principal, que es el caso habitual.
     */
    private String tipoKey;

    /** Duración y precio propios, que dependen del tipo. Si no vienen, se heredan igual. */
    private Integer duracionMinutos;
    private java.math.BigDecimal precioPorSesion;
}
