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

    private String observacion;

    @JsonProperty("motivo_cancelacion")
    private String motivoCancelacion;

    @JsonProperty("notas_previas")
    private String notasPrevias;

    @JsonProperty("notas_post")
    private String notasPost;

    @JsonProperty("link_videollamada")
    private String linkVideollamada;

    @JsonProperty("recordatorio_enviado")
    private Boolean recordatorioEnviado;
}
