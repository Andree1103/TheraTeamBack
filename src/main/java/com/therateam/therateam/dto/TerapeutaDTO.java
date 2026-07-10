package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TerapeutaDTO {

    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String cmp;
    private String telefono;
    private String fotoUrl;
    private String horarioDescripcion;
    private Boolean activo;

    private TipoInfo tipoTerapeuta;
    private AreaInfo area;
    private List<EspecialidadInfo> especialidades;

    @Data
    @NoArgsConstructor
    public static class TipoInfo {
        private Long id;
        private String key;
        private String nombre;
    }

    @Data
    @NoArgsConstructor
    public static class AreaInfo {
        private Long id;
        private String key;
        private String nombre;
    }

    @Data
    @NoArgsConstructor
    public static class EspecialidadInfo {
        private Long id;
        private String key;
        private String nombre;
    }
}
