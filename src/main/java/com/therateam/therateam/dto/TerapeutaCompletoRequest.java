package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TerapeutaCompletoRequest {

    // "existente" | "nuevo"
    private String modo;

    // Solo cuando modo = "existente"
    private Long usuarioId;

    // Solo cuando modo = "nuevo" (también usados en edición para actualizar datos del usuario)
    private String nombre;
    private String apellido;
    private String email;
    private String password;
    private Long sedeId;

    // Siempre (campos del terapeuta)
    private Long tipoTerapeutaId;
    private String cmp;
    private String telefono;
    private String fotoUrl;
    private String horarioDescripcion;
    private Boolean activo;
    private List<Long> especialidadIds;
}
