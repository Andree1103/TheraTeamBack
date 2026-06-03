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
    private String notasPost;
    private List<MetricaInput> metricas;

    @Data
    @NoArgsConstructor
    public static class MetricaInput {
        private String metrica;
        private BigDecimal valor;
        private String unidad;
    }
}
