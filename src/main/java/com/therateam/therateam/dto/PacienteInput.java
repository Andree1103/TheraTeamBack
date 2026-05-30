package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PacienteInput {
    private String dni;
    private String nombre;
    private String apellido;
    private String telefono;
    private String correo;
}
