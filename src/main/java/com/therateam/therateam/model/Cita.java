package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "citas")
@Data @NoArgsConstructor @AllArgsConstructor
public class Cita {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Paciente directo de la cita — ya NO depende de pasar por sesión→tratamiento. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    /** Tipo de terapia directo de la cita (duración/capacidad/nombre) — independiente de si hay paquete. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapia_id")
    private TipoTerapia tipoTerapia;

    /** Precio acordado para esta cita puntual (si no está ligada a un paquete que ya trae su propio precio). */
    private BigDecimal precio;

    /**
     * Suma de lo ya cobrado a esta cita puntual (adelantos incluidos) — solo aplica a citas
     * SIN paquete; las citas de paquete se cobran contra el tratamiento, no aquí. deuda = precio - montoPagado.
     */
    private BigDecimal montoPagado;

    /**
     * Opcional: solo se llena cuando la cita se creó eligiendo un paquete existente —
     * indica qué sesión de ese paquete cubre esta cita. Una cita "normal" (sin paquete)
     * no tiene sesión; al atenderse se registra como AtencionClinica, no como Sesion.
     */
    @JsonIgnoreProperties({"citaActiva", "tratamiento"})
    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "sesion_id")
    private Sesion sesion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modalidad_id")
    private CatModalidad modalidad;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Integer duracionMinutos;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id")
    private CatEstadoCita estado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_pago_id")
    private CatEstadoPagoCita estadoPago;

    private String linkVideollamada;
    private String notasPrevias;
    private Boolean recordatorioEnviado;

    /** Clasificación de la cita: FIJO (horario recurrente permanente), EVENTUAL (ocasional) o SOLO_HOY (única vez, sin intención de repetirse). */
    private String tipoRecurrencia;

    // Autorreferencia: apunta a la cita anterior si es reprogramación
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reprogramacion_de")
    private Cita reprogramacionDe;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (recordatorioEnviado == null) recordatorioEnviado = false;
        if (tipoRecurrencia == null) tipoRecurrencia = "EVENTUAL";
        if (montoPagado == null) montoPagado = BigDecimal.ZERO;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
