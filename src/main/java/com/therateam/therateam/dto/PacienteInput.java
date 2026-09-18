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
     *
     * DURACIÓN Y PRECIO NO SE SEPARAN. Aunque el tipo elegido tenga otra duración o otro precio
     * de catálogo, la cita del acompañante hereda los de la cita principal: es el MISMO bloque
     * con el mismo terapeuta, así que empieza y termina cuando ese bloque empieza y termina, y
     * se cobra lo que se acordó para él. Lo que cambia es la terapia que queda registrada.
     */
    private String tipoKey;
}
