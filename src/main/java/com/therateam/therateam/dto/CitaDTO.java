package com.therateam.therateam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CitaDTO {

    private Long id;

    @JsonProperty("sesion_id")
    private Long sesionId;

    @JsonProperty("numero_sesion")
    private Integer numeroSesion;

    @JsonProperty("total_sesiones")
    private Integer totalSesiones;

    @JsonProperty("paciente_id")
    private Long pacienteId;

    @JsonProperty("paciente_nombre")
    private String pacienteNombre;

    @JsonProperty("paciente_apellido")
    private String pacienteApellido;

    @JsonProperty("paciente_dni")
    private String pacienteDni;

    @JsonProperty("paciente_telefono")
    private String pacienteTelefono;

    @JsonProperty("paciente_correo")
    private String pacienteCorreo;

    @JsonProperty("terapeuta_id")
    private Long terapeutaId;

    @JsonProperty("terapeuta_nombre")
    private String terapeutaNombre;

    @JsonProperty("tipo_terapia_key")
    private String tipoTerapiaKey;

    @JsonProperty("tipo_terapia_nombre")
    private String tipoTerapiaNombre;

    @JsonProperty("duracion_minutos")
    private Integer duracionMinutos;

    @JsonProperty("fecha_inicio")
    private LocalDateTime fechaInicio;

    @JsonProperty("fecha_fin")
    private LocalDateTime fechaFin;

    private String estado;

    @JsonProperty("estado_nombre")
    private String estadoNombre;

    @JsonProperty("estado_color")
    private String estadoColor;

    private String modalidad;
    private String observacion;

    @JsonProperty("notas_previas")
    private String notasPrevias;

    @JsonProperty("link_videollamada")
    private String linkVideollamada;

    @JsonProperty("recordatorio_enviado")
    private Boolean recordatorioEnviado;
}
