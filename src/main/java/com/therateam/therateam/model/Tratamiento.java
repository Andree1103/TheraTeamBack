package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tratamientos")
@Data @NoArgsConstructor @AllArgsConstructor
public class Tratamiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El paciente es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @NotNull(message = "El terapeuta es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    @NotNull(message = "El tipo de terapia es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapia_id")
    private TipoTerapia tipoTerapia;

    @NotBlank(message = "El nombre del paquete es obligatorio")
    private String nombre;
    @NotNull(message = "El total de sesiones es obligatorio")
    @Min(value = 1, message = "El total de sesiones debe ser al menos 1")
    private Integer totalSesiones;
    @NotNull(message = "El precio por sesión es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio por sesión debe ser mayor a 0")
    private BigDecimal precioPorSesion;

    // GENERATED ALWAYS AS — solo lectura
    @Column(insertable = false, updatable = false)
    private BigDecimal montoTotal;

    @NotNull(message = "El estado es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id")
    private CatEstadoTratamiento estado;

    private Integer sesionesAtendidas;

    // GENERATED ALWAYS AS — solo lectura
    @Column(name = "sesiones_pendientes", insertable = false, updatable = false)
    private Integer sesionesPendientes;

    private BigDecimal totalCobrado;

    @Column(name = "saldo_a_favor")
    private BigDecimal saldoAFavor;
    private LocalDate fechaInicio;
    private String notas;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (sesionesAtendidas == null) sesionesAtendidas = 0;
        if (totalCobrado == null) totalCobrado = BigDecimal.ZERO;
        if (saldoAFavor == null) saldoAFavor = BigDecimal.ZERO;
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }
}
