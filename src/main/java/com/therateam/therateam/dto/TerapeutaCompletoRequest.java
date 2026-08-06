package com.therateam.therateam.dto;

import jakarta.validation.constraints.AssertTrue;
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
    private Long areaId;
    private String cmp;
    private String telefono;
    private String fotoUrl;
    private String horarioDescripcion;
    private Boolean activo;
    private List<Long> especialidadIds;

    /** "existente" necesita usuarioId; "nuevo" necesita al menos nombre/apellido/email. */
    @AssertTrue(message = "Faltan datos: si modo es 'existente' se requiere usuarioId, si es 'nuevo' se requieren nombre, apellido y email")
    public boolean isDatosSegunModoValidos() {
        if ("existente".equals(modo)) return usuarioId != null;
        if ("nuevo".equals(modo)) {
            return notBlank(nombre) && notBlank(apellido) && notBlank(email);
        }
        return false;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
