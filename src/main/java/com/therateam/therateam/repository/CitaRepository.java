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
            ac.conDevolucion, ac.montoDevuelto, ac.createdAt
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
            ac.conDevolucion, ac.montoDevuelto, ac.createdAt
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
            ac.conDevolucion, ac.montoDevuelto, ac.createdAt
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

    /**
     * Cuantas citas tiene cada paquete, cuantas atendidas y cuantas no asistidas, de una sola vez.
     *
     * Agrupado a proposito: contarlo paquete por paquete al listar seria una consulta por fila.
     * Devuelve [tratamientoId, total, atendidas, noAsistidas].
     */
    @Query("""
        SELECT s.tratamiento.id,
               COUNT(c),
               SUM(CASE WHEN e.key = 'ASISTIDA'   THEN 1 ELSE 0 END),
               SUM(CASE WHEN e.key = 'NO_ASISTIO' THEN 1 ELSE 0 END)
        FROM Cita c
        JOIN c.sesion s
        LEFT JOIN c.estado e
        WHERE s.tratamiento.id IN :ids AND c.eliminado = false
        GROUP BY s.tratamiento.id
        """)
    List<Object[]> contarCitasPorTratamiento(@Param("ids") java.util.Collection<Long> ids);

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
            ac.conDevolucion, ac.montoDevuelto, ac.createdAt
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

    /** Las citas colgadas de esas sesiones — para poder borrar un paquete entero de una vez. */
    List<Cita> findBySesionIdIn(java.util.Collection<Long> sesionIds);

    /**
     * Citas sueltas del paciente que se podrian meter en un paquete de ese terapeuta y tipo.
     *
     * Las condiciones son las que hacen que absorberla sea seguro:
     *   - suelta (sesion_id IS NULL): si ya es de un paquete, moverla seria sacarla de otro.
     *   - sin un sol cobrado y sin ningun pago apuntandole: en cuanto hay dinero de por medio,
     *     ese dinero esta ligado a la cita y no al paquete, y absorberla lo dejaria colgando.
     *   - en pie: una anulada, una inasistencia o una reprogramada no son sesiones futuras.
     *   - desde hoy: meter en un paquete nuevo una cita de hace tres semanas no es lo que
     *     nadie quiere decir con "las citas que ya tiene agendadas".
     */
    @Query("""
        SELECT c FROM Cita c
         WHERE c.paciente.id = :pacienteId
           AND c.terapeuta.id = :terapeutaId
           AND c.tipoTerapia.id = :tipoTerapiaId
           AND c.sesion IS NULL
           AND c.eliminado = false
           AND c.fechaInicio >= :desde
           AND c.estado.key NOT IN :estadosFuera
           AND COALESCE(c.montoPagado, 0) = 0
           AND c.estadoPago.key = 'SIN_PAGO'
           AND NOT EXISTS (SELECT 1 FROM Pago p WHERE p.cita.id = c.id)
         ORDER BY c.fechaInicio
        """)
    List<Cita> absorbiblesEnPaquete(@Param("pacienteId") Long pacienteId,
                                    @Param("terapeutaId") Long terapeutaId,
                                    @Param("tipoTerapiaId") Long tipoTerapiaId,
                                    @Param("desde") java.time.LocalDateTime desde,
                                    @Param("estadosFuera") java.util.Collection<String> estadosFuera);
}
