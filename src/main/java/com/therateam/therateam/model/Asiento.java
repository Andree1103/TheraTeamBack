package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un hecho de dinero, con sus líneas.
 *
 * El asiento no guarda importes: los guardan sus líneas, y la suma de las de un lado tiene que
 * igualar la del otro. Esa igualdad la comprueba la base (trigger tg_asiento_cuadra), no este
 * código — a propósito: lo que vive solo en Java se puede esquivar por un camino nuevo, y esta
 * semana se vio tres veces.
 *
 * FASE 1: esto solo se escribe. Las pantallas siguen leyendo de pagos, citas y pacientes como
 * hasta ahora. Un reconciliador compara los dos mundos para encontrar, en producción y sin
 * riesgo, los caminos que todavía no dejan su asiento.
 */
@Entity
@Table(name = "asientos")
@Data @NoArgsConstructor @AllArgsConstructor
public class Asiento {

    public enum Tipo { COBRO, DEVOLUCION, ANULACION, AJUSTE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    /** De qué pago salió. Solo mientras los dos mundos conviven; en la fase 3 sobra. */
    @Column(name = "pago_id")
    private Long pagoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    private String nota;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Las líneas que se van a guardar con este asiento.
     *
     * @Transient y no @OneToMany: las escribe LibroService después de guardar la cabecera,
     * porque hasta entonces no hay id del que colgarlas. Dejarlo como relación obligaría a
     * mantener las dos puntas sincronizadas para nada — aquí el asiento se construye, se guarda
     * y no se vuelve a tocar.
     */
    @Transient
    private List<AsientoLinea> lineas = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fecha == null) fecha = LocalDateTime.now();
    }

    /**
     * Añade una línea. Un importe de cero o negativo no añade nada: no es un hecho, y la base
     * lo rechazaría. Así quien construye el asiento no tiene que ir preguntando "¿hubo algo?"
     * antes de cada línea.
     */
    public Asiento linea(AsientoLinea.Concepto concepto, BigDecimal importe,
                         Long metodoId, Long citaId, Long tratamientoId) {
        if (importe == null || importe.compareTo(BigDecimal.ZERO) <= 0) return this;
        AsientoLinea l = new AsientoLinea();
        l.setConcepto(concepto);
        l.setImporte(importe);
        l.setMetodoId(metodoId);
        l.setCitaId(citaId);
        l.setTratamientoId(tratamientoId);
        lineas.add(l);
        return this;
    }

    public Asiento linea(AsientoLinea.Concepto concepto, BigDecimal importe) {
        return linea(concepto, importe, null, null, null);
    }
}
