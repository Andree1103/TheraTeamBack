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

    /** Constructor usado por la proyección JPQL (SELECT new ...) — el orden debe calzar con la query. */
    public TratamientoDTO(Long id, String nombre,
                           Long pacienteId, String pacienteNombre, String pacienteApellido,
                           String pacienteDni, String pacienteTelefono,
                           Long terapeutaId, String terapeutaNombre,
                           String tipoTerapiaKey, String tipoTerapiaNombre,
                           String estadoKey, String estadoNombre, String estadoColor,
                           Integer totalSesiones, Integer sesionesAtendidas, Integer sesionesPendientes,
                           BigDecimal montoTotal, BigDecimal precioPorSesion,
                           BigDecimal totalCobrado, BigDecimal saldoAFavor,
                           LocalDate fechaInicio, String notas) {
        this.id = id;
        this.nombre = nombre;
        this.pacienteId = pacienteId;
        this.pacienteNombre = pacienteNombre;
        this.pacienteApellido = pacienteApellido;
        this.pacienteDni = pacienteDni;
        this.pacienteTelefono = pacienteTelefono;
        this.terapeutaId = terapeutaId;
        this.terapeutaNombre = terapeutaNombre;
        this.tipoTerapiaKey = tipoTerapiaKey;
        this.tipoTerapiaNombre = tipoTerapiaNombre;
        this.estadoKey = estadoKey;
        this.estadoNombre = estadoNombre;
        this.estadoColor = estadoColor;
        this.totalSesiones = totalSesiones;
        this.sesionesAtendidas = sesionesAtendidas;
        this.sesionesPendientes = sesionesPendientes;
        this.montoTotal = montoTotal;
        this.precioPorSesion = precioPorSesion;
        this.totalCobrado = totalCobrado;
        this.saldoAFavor = saldoAFavor;
        this.fechaInicio = fechaInicio;
        this.notas = notas;
    }
}
