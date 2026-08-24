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
}
