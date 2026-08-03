package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PermisoDTO {
    private String modulo;
    private boolean crear;
    private boolean editar;
    private boolean eliminar;
}
