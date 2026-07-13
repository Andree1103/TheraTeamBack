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

    /** Constructor usado por la proyección JPQL principal (sin especialidades: se cargan aparte en batch). */
    public TerapeutaDTO(Long id, String nombre, String apellido, String email, String cmp,
                         String telefono, String fotoUrl, String horarioDescripcion, Boolean activo,
                         TipoInfo tipoTerapeuta, AreaInfo area) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.cmp = cmp;
        this.telefono = telefono;
        this.fotoUrl = fotoUrl;
        this.horarioDescripcion = horarioDescripcion;
        this.activo = activo;
        this.tipoTerapeuta = tipoTerapeuta;
        this.area = area;
    }

    @Data
    @NoArgsConstructor
    public static class TipoInfo {
        private Long id;
        private String key;
        private String nombre;

        public TipoInfo(Long id, String key, String nombre) {
            this.id = id; this.key = key; this.nombre = nombre;
        }
    }

    @Data
    @NoArgsConstructor
    public static class AreaInfo {
        private Long id;
        private String key;
        private String nombre;

        public AreaInfo(Long id, String key, String nombre) {
            this.id = id; this.key = key; this.nombre = nombre;
        }
    }

    @Data
    @NoArgsConstructor
    public static class EspecialidadInfo {
        private Long id;
        private String key;
        private String nombre;

        public EspecialidadInfo(Long id, String key, String nombre) {
            this.id = id; this.key = key; this.nombre = nombre;
        }
    }
}
