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
    /** Concepto libre para pagos adicionales (ej. "Material adicional") — null en pagos normales. */
    private String concepto;
    /** true = cobro adicional (ingreso aparte, no descuenta deuda ni genera saldo a favor). */
    private Boolean esAdicional;
    /** true = este registro es la devolución de otro pago (dinero que salió, no que entró). */
    private Boolean esDevolucion;
    /** Nombre del usuario que registró el pago. */
    private String usuarioCreacionNombre;

    public PagoDTO(Long id,
                    Long tratamientoId, String tratamientoNombre, String terapeutaNombre, String tipoTerapiaNombre,
                    Long pacienteId, String pacienteNombre, String pacienteApellido, String pacienteDni,
                    Long metodoId, String metodoNombre,
                    BigDecimal montoRecibido, BigDecimal montoAplicado,
                    BigDecimal saldoGenerado, BigDecimal saldoPrevio,
                    String referencia, String notas,
                    LocalDateTime fechaPago, LocalDateTime createdAt,
                    String concepto, Boolean esAdicional, Boolean esDevolucion, String usuarioCreacionNombre) {
        this.id = id;
        this.tratamiento = new TratamientoInfo(tratamientoId, tratamientoNombre, terapeutaNombre, tipoTerapiaNombre);
        this.paciente = new PacienteInfo(pacienteId, pacienteNombre, pacienteApellido, pacienteDni);
        this.metodo = new MetodoInfo(metodoId, metodoNombre);
        this.montoRecibido = montoRecibido;
        this.montoAplicado = montoAplicado;
        this.saldoGenerado = saldoGenerado;
        this.saldoPrevio = saldoPrevio;
        this.referencia = referencia;
        this.notas = notas;
        this.fechaPago = fechaPago;
        this.createdAt = createdAt;
        this.concepto = concepto;
        this.esAdicional = esAdicional;
        this.esDevolucion = esDevolucion;
        this.usuarioCreacionNombre = usuarioCreacionNombre;
    }

    @Data
    @NoArgsConstructor
    public static class TratamientoInfo {
        private Long id;
        private String nombre;
        private String terapeutaNombre;
        private String tipoTerapiaNombre;

        public TratamientoInfo(Long id, String nombre, String terapeutaNombre, String tipoTerapiaNombre) {
            this.id = id; this.nombre = nombre;
            this.terapeutaNombre = terapeutaNombre; this.tipoTerapiaNombre = tipoTerapiaNombre;
        }
    }

    @Data
    @NoArgsConstructor
    public static class PacienteInfo {
        private Long id;
        private String nombre;
        private String apellido;
        private String dni;

        public PacienteInfo(Long id, String nombre, String apellido, String dni) {
            this.id = id; this.nombre = nombre; this.apellido = apellido; this.dni = dni;
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
