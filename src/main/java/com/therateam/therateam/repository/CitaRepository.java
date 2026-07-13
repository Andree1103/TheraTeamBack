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
     * (ej. CANCELADA) y opcionalmente excluyendo la propia cita (para updates).
     */
    List<Cita> findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotAndIdNot(
            Long terapeutaId, LocalDateTime fechaFin, LocalDateTime fechaInicio, String estadoKey, Long excludeId);

    List<Cita> findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNot(
            Long terapeutaId, LocalDateTime fechaFin, LocalDateTime fechaInicio, String estadoKey);

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
            ep.key, ep.nombre, ep.color
        )
        FROM Cita c
        LEFT JOIN c.sesion s
        LEFT JOIN s.tratamiento t
        LEFT JOIN t.paciente p
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN c.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN c.estado e
        LEFT JOIN c.modalidad m
        LEFT JOIN c.estadoPago ep
        """)
    Page<CitaDTO> findAllProjected(Pageable pageable);

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
            ep.key, ep.nombre, ep.color
        )
        FROM Cita c
        LEFT JOIN c.sesion s
        LEFT JOIN s.tratamiento t
        LEFT JOIN t.paciente p
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN c.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN c.estado e
        LEFT JOIN c.modalidad m
        LEFT JOIN c.estadoPago ep
        WHERE (:fechaInicio IS NULL OR c.fechaInicio >= :fechaInicio)
          AND (:fechaFin IS NULL OR c.fechaInicio <= :fechaFin)
          AND (:terapeuta IS NULL OR LOWER(CONCAT(u.nombre, ' ', u.apellido)) LIKE LOWER(CONCAT('%', :terapeuta, '%')))
        """)
    Page<CitaDTO> findByFiltrosProjected(@Param("fechaInicio") LocalDateTime fechaInicio,
                                          @Param("fechaFin") LocalDateTime fechaFin,
                                          @Param("terapeuta") String terapeuta,
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
            ep.key, ep.nombre, ep.color
        )
        FROM Cita c
        LEFT JOIN c.sesion s
        LEFT JOIN s.tratamiento t
        LEFT JOIN t.paciente p
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN c.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN c.estado e
        LEFT JOIN c.modalidad m
        LEFT JOIN c.estadoPago ep
        WHERE c.id = :id
        """)
    java.util.Optional<CitaDTO> findByIdProjected(@Param("id") Long id);
}
