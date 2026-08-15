package com.therateam.therateam.repository;
import com.therateam.therateam.dto.PagoDTO;
import com.therateam.therateam.model.Pago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByTratamientoId(Long tratamientoId);
    List<Pago> findByPacienteId(Long pacienteId);

    /** Ingresos del día agrupados por método de pago — para el cierre de caja. */
    @Query("""
        SELECT m.id, m.nombre, SUM(pg.montoRecibido)
        FROM Pago pg
        LEFT JOIN pg.metodo m
        WHERE pg.fechaPago >= :inicioDia AND pg.fechaPago < :finDia
        GROUP BY m.id, m.nombre
        """)
    List<Object[]> sumMontoPorMetodoEntreFechas(@Param("inicioDia") LocalDateTime inicioDia,
                                                 @Param("finDia") LocalDateTime finDia);

    /**
     * Proyección liviana para listados: evita la cadena EAGER completa de Pago.tratamiento
     * (Terapeuta->Usuario/TipoTerapeuta/Area/especialidades, TipoTerapia->Area, etc.) — pero sí
     * trae terapeuta/tipo/DNI planos, que la tabla de Pagos sí necesita mostrar.
     */
    @Query("""
        SELECT new com.therateam.therateam.dto.PagoDTO(
            pg.id,
            t.id, t.nombre, CONCAT(u.nombre, ' ', u.apellido), tt.nombre,
            p.id, p.nombre, p.apellido, p.dni,
            m.id, m.nombre,
            pg.montoRecibido, pg.montoAplicado, pg.saldoGenerado, pg.saldoPrevio,
            pg.referencia, pg.notas, pg.fechaPago, pg.createdAt
        )
        FROM Pago pg
        LEFT JOIN pg.tratamiento t
        LEFT JOIN t.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN pg.paciente p
        LEFT JOIN pg.metodo m
        ORDER BY pg.fechaPago DESC
        """)
    List<PagoDTO> findAllProjected();

    /**
     * `paciente`, `referencia`, `metodoId`, `tienePaquete`, `montoMin/Max`, `fechaInicio/Fin` nulos = sin restringir por ese filtro.
     * `tienePaquete`: true = solo pagos ligados a un paquete, false = solo pagos sin paquete (cita suelta).
     */
    @Query("""
        SELECT new com.therateam.therateam.dto.PagoDTO(
            pg.id,
            t.id, t.nombre, CONCAT(u.nombre, ' ', u.apellido), tt.nombre,
            p.id, p.nombre, p.apellido, p.dni,
            m.id, m.nombre,
            pg.montoRecibido, pg.montoAplicado, pg.saldoGenerado, pg.saldoPrevio,
            pg.referencia, pg.notas, pg.fechaPago, pg.createdAt
        )
        FROM Pago pg
        LEFT JOIN pg.tratamiento t
        LEFT JOIN t.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN pg.paciente p
        LEFT JOIN pg.metodo m
        WHERE (CAST(:paciente AS string) IS NULL
               OR LOWER(CONCAT(p.nombre, ' ', p.apellido)) LIKE LOWER(CONCAT('%', CAST(:paciente AS string), '%')))
          AND (CAST(:referencia AS string) IS NULL OR LOWER(pg.referencia) LIKE LOWER(CONCAT('%', CAST(:referencia AS string), '%')))
          AND (CAST(:metodoId AS long) IS NULL OR m.id = :metodoId)
          AND (CAST(:tienePaquete AS boolean) IS NULL
               OR (:tienePaquete = TRUE AND t.id IS NOT NULL)
               OR (:tienePaquete = FALSE AND t.id IS NULL))
          AND (CAST(:montoMin AS big_decimal) IS NULL OR pg.montoRecibido >= :montoMin)
          AND (CAST(:montoMax AS big_decimal) IS NULL OR pg.montoRecibido <= :montoMax)
          AND (CAST(:fechaInicio AS timestamp) IS NULL OR pg.fechaPago >= :fechaInicio)
          AND (CAST(:fechaFin AS timestamp) IS NULL OR pg.fechaPago <= :fechaFin)
        """)
    Page<PagoDTO> buscarPaged(@Param("paciente") String paciente,
                               @Param("referencia") String referencia,
                               @Param("metodoId") Long metodoId,
                               @Param("tienePaquete") Boolean tienePaquete,
                               @Param("montoMin") BigDecimal montoMin,
                               @Param("montoMax") BigDecimal montoMax,
                               @Param("fechaInicio") LocalDateTime fechaInicio,
                               @Param("fechaFin") LocalDateTime fechaFin,
                               Pageable pageable);

    @Query("""
        SELECT new com.therateam.therateam.dto.PagoDTO(
            pg.id,
            t.id, t.nombre, CONCAT(u.nombre, ' ', u.apellido), tt.nombre,
            p.id, p.nombre, p.apellido, p.dni,
            m.id, m.nombre,
            pg.montoRecibido, pg.montoAplicado, pg.saldoGenerado, pg.saldoPrevio,
            pg.referencia, pg.notas, pg.fechaPago, pg.createdAt
        )
        FROM Pago pg
        LEFT JOIN pg.tratamiento t
        LEFT JOIN t.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN pg.paciente p
        LEFT JOIN pg.metodo m
        WHERE p.id = :pacienteId
        ORDER BY pg.fechaPago DESC
        """)
    List<PagoDTO> findByPacienteIdProjected(@Param("pacienteId") Long pacienteId);

    @Query("""
        SELECT new com.therateam.therateam.dto.PagoDTO(
            pg.id,
            t.id, t.nombre, CONCAT(u.nombre, ' ', u.apellido), tt.nombre,
            p.id, p.nombre, p.apellido, p.dni,
            m.id, m.nombre,
            pg.montoRecibido, pg.montoAplicado, pg.saldoGenerado, pg.saldoPrevio,
            pg.referencia, pg.notas, pg.fechaPago, pg.createdAt
        )
        FROM Pago pg
        LEFT JOIN pg.tratamiento t
        LEFT JOIN t.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN pg.paciente p
        LEFT JOIN pg.metodo m
        WHERE t.id = :tratamientoId
        ORDER BY pg.fechaPago DESC
        """)
    List<PagoDTO> findByTratamientoIdProjected(@Param("tratamientoId") Long tratamientoId);
}
