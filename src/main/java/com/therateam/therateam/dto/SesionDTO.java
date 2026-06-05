package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SesionDTO {

    private Long id;
    private Integer numero;
    private EstadoInfo estado;
    private CitaActivaInfo citaActiva;

    @Data
    @NoArgsConstructor
    public static class EstadoInfo {
        private Long id;
        private String key;
        private String nombre;
        private String colorHex;
    }

    @Data
    @NoArgsConstructor
    public static class CitaActivaInfo {
        private Long id;
        private LocalDateTime fechaInicio;
        private Integer duracionMinutos;
        private EstadoInfo estado;
        private ModalidadInfo modalidad;
        private String estadoPagoKey;
        private String estadoPagoNombre;
        private String estadoPagoColor;
    }

    @Data
    @NoArgsConstructor
    public static class ModalidadInfo {
        private String key;
        private String nombre;
    }
}
