package com.therateam.therateam.repository;

import com.therateam.therateam.model.CuentaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaCreditoRepository extends JpaRepository<CuentaCredito, Long> {

    /** Una cuenta por titular (UNIQUE en la base), así que devuelve a lo sumo una. */
    Optional<CuentaCredito> findByTitularId(Long titularId);

    /**
     * La última cuenta que le pagó una CITA a este paciente, con la fecha de esa atención.
     *
     * Es lo que el formulario sugiere al fiar. Un paciente puede estar en varias cuentas, y
     * preguntar "¿quién paga?" sin proponer nada hace que alguien elija mal con prisa; lo último
     * que pasó es la mejor apuesta, y se cambia en el mismo desplegable.
     *
     * Devuelve filas {cuentaId, fecha} de la más reciente a la más vieja: el service se queda con
     * la primera y la compara con la de paquetes.
     */
    @Query("""
        SELECT c.cuentaCredito.id, MAX(c.fechaInicio)
          FROM Cita c
         WHERE c.paciente.id = :pacienteId
           AND c.cuentaCredito IS NOT NULL
           AND c.eliminado = false
         GROUP BY c.cuentaCredito.id
         ORDER BY MAX(c.fechaInicio) DESC
        """)
    List<Object[]> ultimasCuentasPorCitas(@Param("pacienteId") Long pacienteId);

    /** Lo mismo para los PAQUETES: su deuda vive en el tratamiento, no en sus citas. */
    @Query("""
        SELECT t.cuentaCredito.id, MAX(t.fechaInicio)
          FROM Tratamiento t
         WHERE t.paciente.id = :pacienteId
           AND t.cuentaCredito IS NOT NULL
         GROUP BY t.cuentaCredito.id
         ORDER BY MAX(t.fechaInicio) DESC
        """)
    List<Object[]> ultimasCuentasPorPaquetes(@Param("pacienteId") Long pacienteId);

    boolean existsByTitularId(Long titularId);

    List<CuentaCredito> findByTipoOrderByIdAsc(CuentaCredito.Tipo tipo);

    /**
     * Cuánto debe una cuenta en CITAS SUELTAS (las de siempre y las de un lote masivo).
     *
     * La cuenta no guarda saldo: se suma, con la misma aritmética que usa el resto de la
     * aplicación (precio - montoPagado). Así el crédito no introduce una segunda forma de contar
     * dinero, que es de donde salieron los saldos inflados de octubre.
     *
     * Se excluyen las citas que no se van a cobrar nunca —anuladas, canceladas, reprogramadas— y
     * las eliminadas. Una inasistencia SÍ cuenta: si se atendió a crédito y el paciente no vino,
     * la clínica decide si la descuenta o la perdona, y mientras no lo decida sigue debiéndose.
     *
     * Las citas de paquete no aparecen nunca aquí: la base impide cargarlas
     * (ck_citas_credito_solo_sueltas) porque su deuda la lleva el tratamiento.
     *
     * COALESCE para que una cuenta sin citas devuelva 0 y no null.
     */
    @Query("""
        SELECT COALESCE(SUM(COALESCE(c.precio, 0) - COALESCE(c.montoPagado, 0)), 0)
          FROM Cita c
          JOIN c.estado e
         WHERE c.cuentaCredito.id = :cuentaId
           AND c.eliminado = false
           AND e.key NOT IN ('ANULADA', 'REPROGRAMADA', 'CANCELADA_PACIENTE', 'CANCELADA_CLINICA')
        """)
    BigDecimal deudaEnCitasDe(@Param("cuentaId") Long cuentaId);

    /**
     * Cuánto debe una cuenta en PAQUETES.
     *
     * La otra mitad de la deuda, y la razón de que sean dos consultas: un paquete debe
     * montoTotal - totalCobrado, no la suma de sus citas. Contarlo por las dos puntas sumaría el
     * mismo paquete dos veces.
     *
     * Se excluyen los paquetes cancelados: ese dinero ya no se va a cobrar.
     */
    @Query("""
        SELECT COALESCE(SUM(COALESCE(t.montoTotal, 0) - COALESCE(t.totalCobrado, 0)), 0)
          FROM Tratamiento t
          JOIN t.estado e
         WHERE t.cuentaCredito.id = :cuentaId
           AND e.key <> 'CANCELADO'
        """)
    BigDecimal deudaEnPaquetesDe(@Param("cuentaId") Long cuentaId);

    /**
     * El reporte de cobranza, en dos consultas en vez de dos por cuenta.
     *
     * Devuelven Object[] a propósito: el service los cruza con las cuentas que ya tiene cargadas
     * en lugar de volver a traer titular y tipo aquí. Las cuentas sin nada cobrable NO salen (son
     * JOIN sobre citas / tratamientos); el service las completa con deuda 0.
     *
     * Columnas: idCuenta, cuántas, deuda, desde cuándo.
     */
    @Query("""
        SELECT c.cuentaCredito.id,
               COUNT(c),
               COALESCE(SUM(COALESCE(c.precio, 0) - COALESCE(c.montoPagado, 0)), 0),
               MIN(c.fechaInicio)
          FROM Cita c
          JOIN c.estado e
         WHERE c.cuentaCredito IS NOT NULL
           AND c.eliminado = false
           AND e.key NOT IN ('ANULADA', 'REPROGRAMADA', 'CANCELADA_PACIENTE', 'CANCELADA_CLINICA')
         GROUP BY c.cuentaCredito.id
        """)
    List<Object[]> deudaEnCitasDeTodas();

    @Query("""
        SELECT t.cuentaCredito.id,
               COUNT(t),
               COALESCE(SUM(COALESCE(t.montoTotal, 0) - COALESCE(t.totalCobrado, 0)), 0),
               MIN(t.fechaInicio)
          FROM Tratamiento t
          JOIN t.estado e
         WHERE t.cuentaCredito IS NOT NULL
           AND e.key <> 'CANCELADO'
         GROUP BY t.cuentaCredito.id
        """)
    List<Object[]> deudaEnPaquetesDeTodas();

    /** Cuántas citas y paquetes cuelgan de esta cuenta. Se usa antes de borrarla: las FK no
     *  tienen ON DELETE, así que una sola fila basta para que el borrado falle. */
    @Query("SELECT COUNT(c) FROM Cita c WHERE c.cuentaCredito.id = :cuentaId")
    long countCitas(@Param("cuentaId") Long cuentaId);

    @Query("SELECT COUNT(t) FROM Tratamiento t WHERE t.cuentaCredito.id = :cuentaId")
    long countPaquetes(@Param("cuentaId") Long cuentaId);
}
