package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class TratamientoDTO {

    private Long id;
    private String nombre;

    // Paciente
    private Long pacienteId;
    private String pacienteNombre;
    private String pacienteApellido;
    private String pacienteDni;
    private String pacienteTelefono;

    // Terapeuta
    private Long terapeutaId;
    private String terapeutaNombre;

    // Tipo terapia
    private String tipoTerapiaKey;
    private String tipoTerapiaNombre;

    // Estado
    private String estadoKey;
    private String estadoNombre;
    private String estadoColor;

    // Sesiones y financiero
    private Integer totalSesiones;
    private Integer sesionesAtendidas;
    private Integer sesionesPendientes;
    private BigDecimal montoTotal;
    private BigDecimal precioPorSesion;
    private BigDecimal totalCobrado;
    private BigDecimal saldoAFavor;

    private LocalDate fechaInicio;
    private String notas;
}
