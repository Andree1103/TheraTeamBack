package com.therateam.therateam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CitaDTO {

    /** Constructor usado por la proyección JPQL (SELECT new ...) — el orden debe calzar con la query. */
    public CitaDTO(Long id, Long sesionId, Integer numeroSesion, Integer totalSesiones,
                    Long pacienteId, String pacienteNombre, String pacienteApellido,
                    String pacienteDni, String pacienteTelefono, String pacienteCorreo,
                    Long terapeutaId, String terapeutaNombre,
                    String tipoTerapiaKey, String tipoTerapiaNombre,
                    Integer duracionMinutos, LocalDateTime fechaInicio, LocalDateTime fechaFin,
                    String estado, String estadoNombre, String estadoColor,
                    String modalidad, String observacion,
                    String notasPrevias, String linkVideollamada, Boolean recordatorioEnviado,
                    String estadoPagoKey, String estadoPagoNombre, String estadoPagoColor) {
        this.id = id;
        this.sesionId = sesionId;
        this.numeroSesion = numeroSesion;
        this.totalSesiones = totalSesiones;
        this.pacienteId = pacienteId;
        this.pacienteNombre = pacienteNombre;
        this.pacienteApellido = pacienteApellido;
        this.pacienteDni = pacienteDni;
        this.pacienteTelefono = pacienteTelefono;
        this.pacienteCorreo = pacienteCorreo;
        this.terapeutaId = terapeutaId;
        this.terapeutaNombre = terapeutaNombre;
        this.tipoTerapiaKey = tipoTerapiaKey;
        this.tipoTerapiaNombre = tipoTerapiaNombre;
        this.duracionMinutos = duracionMinutos;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.estado = estado;
        this.estadoNombre = estadoNombre;
        this.estadoColor = estadoColor;
        this.modalidad = modalidad;
        this.observacion = observacion;
        this.notasPrevias = notasPrevias;
        this.linkVideollamada = linkVideollamada;
        this.recordatorioEnviado = recordatorioEnviado;
        this.estadoPagoKey = estadoPagoKey;
        this.estadoPagoNombre = estadoPagoNombre;
        this.estadoPagoColor = estadoPagoColor;
    }

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

    @JsonProperty("estado_pago_key")
    private String estadoPagoKey;

    @JsonProperty("estado_pago_nombre")
    private String estadoPagoNombre;

    @JsonProperty("estado_pago_color")
    private String estadoPagoColor;
}
