package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Conteo de un lote de "citas masivas" — cuántas faltan / ya se atendieron / se cancelaron / faltan por crear. */
@Data
@AllArgsConstructor
public class LoteResumenDTO {
    private String loteMasivoId;
    private int total;
    private int atendidas;
    private int pendientes;
    private int canceladas;
    /** Total planeado al crear el lote — null si no se guardó (lotes creados antes de este cambio). */
    private Integer totalPlaneado;
    /** totalPlaneado - citas activas (no canceladas) — null si no hay totalPlaneado. */
    private Integer faltanPorCrear;
}
