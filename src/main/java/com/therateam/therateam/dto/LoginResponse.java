package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String rol;
    private List<String> modulos;
    private List<PermisoDTO> permisos;
    private Long terapeutaId;
    private boolean citasSoloPropias;
    private boolean citasPuedeCrear;
}
