package com.therateam.therateam.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Proyección liviana de Pago para listados: evita cargar la entidad completa, cuyo
 * Pago.tratamiento (EAGER) arrastra a su vez Terapeuta->Usuario/TipoTerapeuta/Area/especialidades
 * y TipoTerapia->Area — mucho más de lo que la UI de pagos necesita.
 */
@Data
@NoArgsConstructor
public class PagoDTO {

    private Long id;
    private TratamientoInfo tratamiento;
    private PacienteInfo paciente;
    private MetodoInfo metodo;
    private BigDecimal montoRecibido;
    private BigDecimal montoAplicado;
    private BigDecimal saldoGenerado;
    private BigDecimal saldoPrevio;
    private String referencia;
    private String notas;
    private LocalDateTime fechaPago;
    private LocalDateTime createdAt;

    public PagoDTO(Long id,
                    Long tratamientoId, String tratamientoNombre,
                    Long pacienteId, String pacienteNombre, String pacienteApellido,
                    Long metodoId, String metodoNombre,
                    BigDecimal montoRecibido, BigDecimal montoAplicado,
                    BigDecimal saldoGenerado, BigDecimal saldoPrevio,
                    String referencia, String notas,
                    LocalDateTime fechaPago, LocalDateTime createdAt) {
        this.id = id;
        this.tratamiento = new TratamientoInfo(tratamientoId, tratamientoNombre);
        this.paciente = new PacienteInfo(pacienteId, pacienteNombre, pacienteApellido);
        this.metodo = new MetodoInfo(metodoId, metodoNombre);
        this.montoRecibido = montoRecibido;
        this.montoAplicado = montoAplicado;
        this.saldoGenerado = saldoGenerado;
        this.saldoPrevio = saldoPrevio;
        this.referencia = referencia;
        this.notas = notas;
        this.fechaPago = fechaPago;
        this.createdAt = createdAt;
    }

    @Data
    @NoArgsConstructor
    public static class TratamientoInfo {
        private Long id;
        private String nombre;

        public TratamientoInfo(Long id, String nombre) { this.id = id; this.nombre = nombre; }
    }

    @Data
    @NoArgsConstructor
    public static class PacienteInfo {
        private Long id;
        private String nombre;
        private String apellido;

        public PacienteInfo(Long id, String nombre, String apellido) {
            this.id = id; this.nombre = nombre; this.apellido = apellido;
        }
    }

    @Data
    @NoArgsConstructor
    public static class MetodoInfo {
        private Long id;
        private String nombre;

        public MetodoInfo(Long id, String nombre) { this.id = id; this.nombre = nombre; }
    }
}
