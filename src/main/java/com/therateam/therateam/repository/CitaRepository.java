package com.therateam.therateam.repository;

import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.model.Cita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {

    /**
     * Citas del terapeuta que se solapan con [fechaInicio, fechaFin), excluyendo un estado
     * (ej. ANULADA), las eliminadas lógicamente, y opcionalmente excluyendo la propia cita
     * (para updates) — una cita eliminada no debe seguir bloqueando ese horario.
     */
    List<Cita> findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndIdNotAndEliminadoFalse(
            Long terapeutaId, LocalDateTime fechaFin, LocalDateTime fechaInicio, List<String> estadoKeys, Long excludeId);

    List<Cita> findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
            Long terapeutaId, LocalDateTime fechaFin, LocalDateTime fechaInicio, List<String> estadoKeys);

    /**
     * Citas del paciente que se solapan con [fechaInicio, fechaFin) — un paciente no puede estar
     * en dos sesiones a la vez, sin importar el terapeuta. Mismo patrón que la validación de
     * disponibilidad del terapeuta, pero del lado del paciente.
     */
    List<Cita> findByPacienteIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndIdNotAndEliminadoFalse(
            Long pacienteId, LocalDateTime fechaFin, LocalDateTime fechaInicio, List<String> estadoKeys, Long excludeId);

    List<Cita> findByPacienteIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
            Long pacienteId, LocalDateTime fechaFin, LocalDateTime fechaInicio, List<String> estadoKeys);

    /**
     * Proyección liviana para listados: trae SOLO las columnas de CitaDTO en vez de la entidad
     * completa (que por sus asociaciones EAGER arrastra sesion→tratamiento→paciente/tipoTerapia,
     * terapeuta→usuario/tipoTerapeuta/area/especialidades, etc. — mucho más de lo que se usa).
     */
    @Query("""
        SELECT new com.therateam.therateam.dto.CitaDTO(
            c.id, s.id, s.numero, t.totalSesiones,
            p.id, p.nombre, p.apellido, p.dni, p.telefono, p.correo,
            ter.id, CONCAT(u.nombre, ' ', u.apellido),
            tt.key, tt.nombre,
            c.duracionMinutos, c.fechaInicio, c.fechaFin,
            e.key, e.nombre, e.colorHex,
            m.key, t.notas,
            c.notasPrevias, c.linkVideollamada, c.recordatorioEnviado,
            ep.key, ep.nombre, ep.color,
            c.tipoRecurrencia, c.precio, c.montoPagado, t.id, t.nombre,
            (SELECT m2.nombre FROM Pago pg2 LEFT JOIN pg2.metodo m2
             WHERE pg2.id = (SELECT MAX(pg3.id) FROM Pago pg3
                              WHERE pg3.cita = c AND pg3.esDevolucion = false AND pg3.esAdicional = false)),
            c.loteMasivoId,
            (SELECT CONCAT(uc.nombre, ' ', uc.apellido) FROM Usuario uc WHERE uc.id = c.usuarioCreacionId),
            c.motivoEstado, rp.id,
            (SELECT MIN(rn.id) FROM Cita rn WHERE rn.reprogramacionDe = c AND rn.eliminado = false),
            ac.conDevolucion, ac.montoDevuelto
        )
        FROM Cita c
        LEFT JOIN c.sesion s
        LEFT JOIN s.tratamiento t
        LEFT JOIN c.paciente p
        LEFT JOIN c.tipoTerapia tt
        LEFT JOIN c.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN c.estado e
        LEFT JOIN c.modalidad m
        LEFT JOIN c.estadoPago ep
        LEFT JOIN c.reprogramacionDe rp
        LEFT JOIN AtencionClinica ac ON ac.cita = c
        WHERE c.eliminado = false
        """)
    Page<CitaDTO> findAllProjected(Pageable pageable);

    /**
     * El medio de pago sale de un JOIN con alias (mUlt) y no de una subconsulta en el SELECT
     * como en las demas proyecciones: asi la columna se puede ORDENAR desde Atenciones
     * (Pageable no puede ordenar por el resultado de una subconsulta correlacionada).
     */
    @Query("""
        SELECT new com.therateam.therateam.dto.CitaDTO(
            c.id, s.id, s.numero, t.totalSesiones,
            p.id, p.nombre, p.apellido, p.dni, p.telefono, p.correo,
            ter.id, CONCAT(u.nombre, ' ', u.apellido),
            tt.key, tt.nombre,
            c.duracionMinutos, c.fechaInicio, c.fechaFin,
            e.key, e.nombre, e.colorHex,
            m.key, t.notas,
            c.notasPrevias, c.linkVideollamada, c.recordatorioEnviado,
            ep.key, ep.nombre, ep.color,
            c.tipoRecurrencia, c.precio, c.montoPagado, t.id, t.nombre,
            mUlt.nombre,
            c.loteMasivoId,
            (SELECT CONCAT(uc.nombre, ' ', uc.apellido) FROM Usuario uc WHERE uc.id = c.usuarioCreacionId),
            c.motivoEstado, rp.id,
            (SELECT MIN(rn.id) FROM Cita rn WHERE rn.reprogramacionDe = c AND rn.eliminado = false),
            ac.conDevolucion, ac.montoDevuelto
        )
        FROM Cita c
        LEFT JOIN c.sesion s
        LEFT JOIN s.tratamiento t
        LEFT JOIN c.paciente p
        LEFT JOIN c.tipoTerapia tt
        LEFT JOIN tt.area a
        LEFT JOIN Pago pgUlt ON pgUlt.id = (SELECT MAX(pg3.id) FROM Pago pg3
                                             WHERE pg3.cita = c AND pg3.esDevolucion = false AND pg3.esAdicional = false)
        LEFT JOIN pgUlt.metodo mUlt
        LEFT JOIN c.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN c.estado e
        LEFT JOIN c.modalidad m
        LEFT JOIN c.estadoPago ep
        LEFT JOIN c.reprogramacionDe rp
        LEFT JOIN AtencionClinica ac ON ac.cita = c
        WHERE c.eliminado = false
          AND (CAST(:fechaInicio AS timestamp) IS NULL OR c.fechaInicio >= :fechaInicio)
          AND (CAST(:fechaFin AS timestamp) IS NULL OR c.fechaInicio <= :fechaFin)
          AND (CAST(:terapeuta AS string) IS NULL OR LOWER(CONCAT(u.nombre, ' ', u.apellido)) LIKE LOWER(CONCAT('%', CAST(:terapeuta AS string), '%')))
          AND (CAST(:terapeutaId AS long) IS NULL OR ter.id = :terapeutaId)
          AND (CAST(:estadoKey AS string) IS NULL
               OR e.key = :estadoKey
               OR (LOCATE(',', :estadoKey) > 0
                   AND LOCATE(CONCAT(',', e.key, ','), CONCAT(',', :estadoKey, ',')) > 0))
          AND (CAST(:paciente AS string) IS NULL
               OR LOWER(CONCAT(p.nombre, ' ', p.apellido)) LIKE LOWER(CONCAT('%', CAST(:paciente AS string), '%')))
          AND (CAST(:areaId AS long) IS NULL OR a.id = :areaId)
          AND (CAST(:metodoPagoId AS long) IS NULL OR mUlt.id = :metodoPagoId)
          AND (CAST(:estadoPagoKey AS string) IS NULL OR ep.key = :estadoPagoKey)
          AND (CAST(:tipoTerapiaKey AS string) IS NULL OR tt.key = :tipoTerapiaKey)
          AND (:sinFiltroTerapeutas = TRUE OR ter.id IN :terapeutaIds)
        """)
    Page<CitaDTO> findByFiltrosProjected(@Param("fechaInicio") LocalDateTime fechaInicio,
                                          @Param("fechaFin") LocalDateTime fechaFin,
                                          @Param("terapeuta") String terapeuta,
                                          @Param("terapeutaId") Long terapeutaId,
                                          @Param("estadoKey") String estadoKey,
                                          @Param("paciente") String paciente,
                                          @Param("areaId") Long areaId,
                                          @Param("metodoPagoId") Long metodoPagoId,
                                          @Param("estadoPagoKey") String estadoPagoKey,
                                          @Param("tipoTerapiaKey") String tipoTerapiaKey,
                                          // La agenda deja marcar varios terapeutas a la vez, por eso una lista
                                          // y no un solo id. El booleano evita el "IN ()" vacio, que no es SQL
                                          // valido: cuando no hay filtro la condicion se cortocircuita y la
                                          // lista ni se mira.
                                          @Param("sinFiltroTerapeutas") boolean sinFiltroTerapeutas,
                                          @Param("terapeutaIds") java.util.Collection<Long> terapeutaIds,
                                          Pageable pageable);

    /** Proyección liviana para un solo registro (GET /{id}). */
    @Query("""
        SELECT new com.therateam.therateam.dto.CitaDTO(
            c.id, s.id, s.numero, t.totalSesiones,
            p.id, p.nombre, p.apellido, p.dni, p.telefono, p.correo,
            ter.id, CONCAT(u.nombre, ' ', u.apellido),
            tt.key, tt.nombre,
            c.duracionMinutos, c.fechaInicio, c.fechaFin,
            e.key, e.nombre, e.colorHex,
            m.key, t.notas,
            c.notasPrevias, c.linkVideollamada, c.recordatorioEnviado,
            ep.key, ep.nombre, ep.color,
            c.tipoRecurrencia, c.precio, c.montoPagado, t.id, t.nombre,
            (SELECT m2.nombre FROM Pago pg2 LEFT JOIN pg2.metodo m2
             WHERE pg2.id = (SELECT MAX(pg3.id) FROM Pago pg3
                              WHERE pg3.cita = c AND pg3.esDevolucion = false AND pg3.esAdicional = false)),
            c.loteMasivoId,
            (SELECT CONCAT(uc.nombre, ' ', uc.apellido) FROM Usuario uc WHERE uc.id = c.usuarioCreacionId),
            c.motivoEstado, rp.id,
            (SELECT MIN(rn.id) FROM Cita rn WHERE rn.reprogramacionDe = c AND rn.eliminado = false),
            ac.conDevolucion, ac.montoDevuelto
        )
        FROM Cita c
        LEFT JOIN c.sesion s
        LEFT JOIN s.tratamiento t
        LEFT JOIN c.paciente p
        LEFT JOIN c.tipoTerapia tt
        LEFT JOIN c.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN c.estado e
        LEFT JOIN c.modalidad m
        LEFT JOIN c.estadoPago ep
        LEFT JOIN c.reprogramacionDe rp
        LEFT JOIN AtencionClinica ac ON ac.cita = c
        WHERE c.id = :id
        """)
    java.util.Optional<CitaDTO> findByIdProjected(@Param("id") Long id);

    /** Todas las citas de un lote de "citas masivas" — para contar cuántas faltan/se atendieron. */
    List<Cita> findByLoteMasivoIdAndEliminadoFalse(String loteMasivoId);

    /** Historial de citas de un paciente (directo, ya no depende de sesión/tratamiento). */
    @Query("""
        SELECT new com.therateam.therateam.dto.CitaDTO(
            c.id, s.id, s.numero, t.totalSesiones,
            p.id, p.nombre, p.apellido, p.dni, p.telefono, p.correo,
            ter.id, CONCAT(u.nombre, ' ', u.apellido),
            tt.key, tt.nombre,
            c.duracionMinutos, c.fechaInicio, c.fechaFin,
            e.key, e.nombre, e.colorHex,
            m.key, t.notas,
            c.notasPrevias, c.linkVideollamada, c.recordatorioEnviado,
            ep.key, ep.nombre, ep.color,
            c.tipoRecurrencia, c.precio, c.montoPagado, t.id, t.nombre,
            (SELECT m2.nombre FROM Pago pg2 LEFT JOIN pg2.metodo m2
             WHERE pg2.id = (SELECT MAX(pg3.id) FROM Pago pg3
                              WHERE pg3.cita = c AND pg3.esDevolucion = false AND pg3.esAdicional = false)),
            c.loteMasivoId,
            (SELECT CONCAT(uc.nombre, ' ', uc.apellido) FROM Usuario uc WHERE uc.id = c.usuarioCreacionId),
            c.motivoEstado, rp.id,
            (SELECT MIN(rn.id) FROM Cita rn WHERE rn.reprogramacionDe = c AND rn.eliminado = false),
            ac.conDevolucion, ac.montoDevuelto
        )
        FROM Cita c
        LEFT JOIN c.sesion s
        LEFT JOIN s.tratamiento t
        LEFT JOIN c.paciente p
        LEFT JOIN c.tipoTerapia tt
        LEFT JOIN c.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN c.estado e
        LEFT JOIN c.modalidad m
        LEFT JOIN c.estadoPago ep
        LEFT JOIN c.reprogramacionDe rp
        LEFT JOIN AtencionClinica ac ON ac.cita = c
        WHERE p.id = :pacienteId AND c.eliminado = false
        ORDER BY c.fechaInicio DESC
        """)
    List<CitaDTO> findByPacienteIdProjected(@Param("pacienteId") Long pacienteId);
}
