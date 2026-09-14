package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class AtencionClinicaRequest {

    private Long citaId;
    private LocalDateTime fechaInicioReal;
    /** Observacion libre. Sigue existiendo junto al SOAP: es lo que cargaron las atenciones
     *  anteriores y sirve para lo que no encaja en ninguno de los cuatro campos. */
    private String notasPost;

    // ── SOAP ──
    private String subjetivo;
    private String objetivo;
    private String analisis;
    private String plan;

    /** Valores de la ficha configurable de la atencion: {claveDelCampo: valor}. */
    private java.util.Map<String, Object> datos;

    private List<MetricaInput> metricas;

    @Data
    @NoArgsConstructor
    public static class MetricaInput {
        private String metrica;
        private BigDecimal valor;
        private String unidad;
    }
}
