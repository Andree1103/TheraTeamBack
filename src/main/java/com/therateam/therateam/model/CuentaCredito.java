package com.therateam.therateam.model;

import com.therateam.therateam.config.SecurityUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Quién responde por las citas que se atienden hoy y se pagan después.
 *
 * El crédito no se cuelga del paciente, se cuelga del titular. Es muy común que un terapeuta
 * haga atender a su madre, a su padre y a su hijo, y lo pague junto a fin de mes: son tres
 * pacientes y una sola deuda, con un solo dueño. Si la deuda viviera en cada paciente habría que
 * reconstruir a mano de quién cobrar.
 *
 * EL TITULAR ES UN USUARIO, no un terapeuta ni un paciente. Los terapeutas tienen usuario_id y
 * cada paciente tiene el suyo (lo crea PacienteService), así que {@link Usuario} ya es la tabla de
 * personas del sistema. Por eso el mismo mecanismo sirve para los dos casos que pidió la clínica
 * —créditos de trabajadores y créditos de pacientes— y lo único que los distingue es el
 * {@link Tipo}, que es lo que filtra el reporte de cobranza.
 *
 * NO GUARDA SALDO. Cuánto debe la cuenta se suma de sus citas (precio - montoPagado). Un total
 * cacheado es justo lo que obligó a escribir db/mantenimiento/2026-10-03-corregir-saldos-inflados.sql;
 * un número que se deriva no se puede desincronizar.
 */
@Entity
@Table(name = "cuentas_credito")
@Data @NoArgsConstructor @AllArgsConstructor
public class CuentaCredito {

    public enum Tipo {
        /** Personal de la clínica y sus familiares. Se cobra a fin de mes, normalmente por planilla. */
        TRABAJADOR,
        /** Paciente al que se le fía y paga a fin de mes. */
        PACIENTE,
        /** Acuerdo con una empresa o colegio. No se deriva de ningún rol: su titular puede ser cualquiera. */
        CONVENIO
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Quien debe. Una cuenta por persona (UNIQUE en la base): con dos cuentas para el mismo
     * titular habría que elegir a cuál cargar cada cita, y el reporte dejaría de responder
     * "cuánto debe Rocío" con una sola fila.
     */
    @NotNull(message = "El titular es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "titular_usuario_id")
    private Usuario titular;

    @NotNull(message = "El tipo de cuenta es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    /**
     * Tope de deuda acumulada. Null = sin tope.
     *
     * AVISA, NO BLOQUEA. Pasarse del tope no impide cargar la cita; hace que la pantalla lo
     * advierta. Bloquear en el mostrador, con el paciente delante, obliga a la recepcionista a
     * discutir con el familiar de una terapeuta — y el atajo de siempre (cobrar un efectivo que
     * no entró) vuelve a ser el camino fácil. El control es que alguien lo vea, no que pelee.
     */
    private BigDecimal limite;

    /** false = no se le cargan citas nuevas. No borra nada: la deuda y la historia siguen. */
    private Boolean activo;

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
        if (activo == null) activo = true;
        usuarioCreacionId = SecurityUtils.currentUserId();
        usuarioModificacionId = usuarioCreacionId;
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); usuarioModificacionId = SecurityUtils.currentUserId(); }

    /** Ante un nulo (fila creada sin pasar por aquí) se asume activa. */
    public boolean estaActiva() { return !Boolean.FALSE.equals(activo); }

    /** Si {@code deuda} se pasa del tope. Sin tope, nunca. Solo para avisar, no para bloquear. */
    public boolean sePasaDelLimite(BigDecimal deuda) {
        return limite != null && deuda != null && deuda.compareTo(limite) > 0;
    }
}
