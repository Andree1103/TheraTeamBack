package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un paciente amarrado a una cuenta de crédito.
 *
 * Esto es lo que contesta "a quién está amarrado el crédito interno": la cuenta es de Rocío y de
 * ella cuelgan su madre, su padre y su hijo. Los amarres desactivados también salen, con
 * {@code activo=false}: son historia de quién respondía cuando se atendió.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreditoAmarreDTO {
    private Long id;
    private Long pacienteId;
    private String pacienteNombre;
    private String pacienteDni;
    private Boolean activo;
    private LocalDateTime desde;
}
