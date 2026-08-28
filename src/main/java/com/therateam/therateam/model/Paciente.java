package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pacientes")
@Data @NoArgsConstructor @AllArgsConstructor
public class Paciente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;
    @NotBlank(message = "El apellido es obligatorio")
    private String apellido;
    @NotBlank(message = "El DNI es obligatorio")
    private String dni;
    private String telefono;
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    private String correo;
    private LocalDate fechaNacimiento;

    /** Solo obligatorios cuando el paciente es menor de 18 años (ver PacienteService) — quien
     *  responde por el paciente ante el consultorio. */
    @Column(name = "dni_apoderado")
    private String dniApoderado;
    @Column(name = "nombre_apoderado")
    private String nombreApoderado;
    @Column(name = "celular_apoderado")
    private String celularApoderado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "origen_id")
    private CatOrigen origen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sede_id")
    private Sede sede;

    private String notas;
    private Boolean activo;

    /**
     * Crédito del paciente (no de un paquete puntual): dinero pagado de más contra cualquier
     * paquete o cita suya que todavía no se ha aplicado a nada — se descuenta automáticamente
     * en el próximo pago que se le registre, sea de un paquete nuevo, uno existente, o una cita
     * suelta. Ver PagoService.
     */
    @Column(name = "saldo_a_favor")
    private BigDecimal saldoAFavor;

    /** Cuenta de acceso del paciente (rol PACIENTE) — se crea junto con el paciente, ver PacienteService. */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    /** Nombre de quien creó el registro — resuelto en el controller a partir de usuarioCreacionId, no persistido. */
    @Transient
    private String usuarioCreacionNombre;

    // Ultimo movimiento del saldo a favor. No se persisten: los rellena el controlador para
    // el reporte de Adelantos, que necesita explicar de donde salio el saldo.
    @Transient
    private String saldoUltimoMotivo;

    @Transient
    private String saldoUltimoTerapeuta;

    @Transient
    private java.time.LocalDateTime saldoUltimaFecha;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); if (activo == null) activo = true;
        if (saldoAFavor == null) saldoAFavor = BigDecimal.ZERO;
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }
}
