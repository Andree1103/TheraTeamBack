package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detalle de cobertura de un tratamiento: cuántas sesiones ya se crearon/agendaron (citas)
 * de las planificadas, y de esas, cuántas ya están pagadas vs pendientes de pago.
 * Distinto de Tratamiento.sesionesAtendidas, que solo cuenta sesiones ya realizadas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TratamientoCoberturaDTO {
    private Integer sesionesCreadas;
    private Integer sesionesPagadas;
    private Integer sesionesPendientesPago;
}
