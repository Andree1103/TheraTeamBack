package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "El paciente es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    /** Tipo de terapia directo de la cita (duración/capacidad/nombre) — independiente de si hay paquete. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_terapia_id")
    private TipoTerapia tipoTerapia;

    /** Solo de entrada: el front manda la key del tipo al editar y el service la resuelve.
     *  No se persiste — la relacion real es tipoTerapia. */
    @Transient
    private String tipoTerapiaKey;

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

    @NotNull(message = "El terapeuta es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terapeuta_id")
    private Terapeuta terapeuta;

    @NotNull(message = "La modalidad es obligatoria")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modalidad_id")
    private CatModalidad modalidad;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDateTime fechaInicio;
    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDateTime fechaFin;
    private Integer duracionMinutos;

    @NotNull(message = "El estado es obligatorio")
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

    @Column(name = "idusuario_creacion", updatable = false)
    private Long usuarioCreacionId;
    @Column(name = "idusuario_modificacion")
    private Long usuarioModificacionId;

    /** Eliminación lógica: "Eliminar" en el front no borra la fila (dinero/historial dependen de
     *  ella), solo la marca oculta — desaparece de agendas y listados pero queda para auditoría. */
    private Boolean eliminado = false;

    /**
     * Agrupador liviano para "citas masivas": todas las citas creadas en un mismo lote comparten
     * este id (generado por el front, no es un id de BD) — permite contar cuántas del grupo
     * faltan/ya se atendieron SIN crear un Tratamiento ni aparecer en el catálogo de Paquetes.
     * Cada cita del grupo sigue siendo independiente en precio/pago, como cualquier cita suelta.
     */
    @Column(name = "lote_masivo_id")
    private String loteMasivoId;

    /** Total de citas planeadas para el lote (mismo valor repetido en todas sus citas) — permite
     *  poner tope (no pasarse del total) y saber cuántas faltan por crear, sin depender de una
     *  tabla aparte. Null si la cita no es parte de un lote. */
    @Column(name = "lote_total_planeado")
    private Integer loteTotalPlaneado;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (recordatorioEnviado == null) recordatorioEnviado = false;
        if (tipoRecurrencia == null) tipoRecurrencia = "EVENTUAL";
        if (montoPagado == null) montoPagado = BigDecimal.ZERO;
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }
}
