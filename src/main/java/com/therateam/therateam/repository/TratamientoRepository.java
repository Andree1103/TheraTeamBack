package com.therateam.therateam.repository;

import com.therateam.therateam.dto.TratamientoDTO;
import com.therateam.therateam.model.Tratamiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TratamientoRepository extends JpaRepository<Tratamiento, Long> {
    List<Tratamiento> findByPacienteId(Long pacienteId);
    List<Tratamiento> findByTerapeutaId(Long terapeutaId);
    Page<Tratamiento> findByPacienteId(Long pacienteId, Pageable pageable);
    Page<Tratamiento> findByTerapeutaId(Long terapeutaId, Pageable pageable);
    List<Tratamiento> findByPacienteIdAndTerapeutaIdAndTipoTerapiaId(Long pacienteId, Long terapeutaId, Long tipoTerapiaId);

    /** Cada parámetro nulo/vacío no restringe — filtros por campo separado (paciente, terapeuta, tipo, estado). */
    @Query("""
        SELECT new com.therateam.therateam.dto.TratamientoDTO(
            t.id, t.nombre,
            p.id, p.nombre, p.apellido, p.dni, p.telefono,
            ter.id, CONCAT(u.nombre, ' ', u.apellido),
            tt.key, tt.nombre,
            es.key, es.nombre, es.colorHex,
            t.totalSesiones, t.sesionesAtendidas, t.sesionesPendientes,
            t.montoTotal, t.precioPorSesion, t.totalCobrado, t.saldoAFavor,
            t.fechaInicio, t.notas
        )
        FROM Tratamiento t
        LEFT JOIN t.paciente p
        LEFT JOIN t.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN t.estado es
        WHERE (CAST(:paciente AS string) IS NULL
               OR LOWER(CONCAT(p.nombre, ' ', p.apellido)) LIKE LOWER(CONCAT('%', CAST(:paciente AS string), '%')))
          AND (CAST(:terapeuta AS string) IS NULL
               OR LOWER(CONCAT(u.nombre, ' ', u.apellido)) LIKE LOWER(CONCAT('%', CAST(:terapeuta AS string), '%')))
          AND (CAST(:tipoTerapiaId AS long) IS NULL OR tt.id = :tipoTerapiaId)
          AND (CAST(:estado AS string) IS NULL OR es.key = :estado)
        """)
    Page<TratamientoDTO> findAllProjected(@Param("paciente") String paciente, @Param("terapeuta") String terapeuta,
                                           @Param("tipoTerapiaId") Long tipoTerapiaId, @Param("estado") String estado,
                                           Pageable pageable);

    @Query("""
        SELECT new com.therateam.therateam.dto.TratamientoDTO(
            t.id, t.nombre,
            p.id, p.nombre, p.apellido, p.dni, p.telefono,
            ter.id, CONCAT(u.nombre, ' ', u.apellido),
            tt.key, tt.nombre,
            es.key, es.nombre, es.colorHex,
            t.totalSesiones, t.sesionesAtendidas, t.sesionesPendientes,
            t.montoTotal, t.precioPorSesion, t.totalCobrado, t.saldoAFavor,
            t.fechaInicio, t.notas
        )
        FROM Tratamiento t
        LEFT JOIN t.paciente p
        LEFT JOIN t.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN t.estado es
        WHERE p.id = :pacienteId
        """)
    Page<TratamientoDTO> findByPacienteIdProjected(@Param("pacienteId") Long pacienteId, Pageable pageable);

    @Query("""
        SELECT new com.therateam.therateam.dto.TratamientoDTO(
            t.id, t.nombre,
            p.id, p.nombre, p.apellido, p.dni, p.telefono,
            ter.id, CONCAT(u.nombre, ' ', u.apellido),
            tt.key, tt.nombre,
            es.key, es.nombre, es.colorHex,
            t.totalSesiones, t.sesionesAtendidas, t.sesionesPendientes,
            t.montoTotal, t.precioPorSesion, t.totalCobrado, t.saldoAFavor,
            t.fechaInicio, t.notas
        )
        FROM Tratamiento t
        LEFT JOIN t.paciente p
        LEFT JOIN t.terapeuta ter
        LEFT JOIN ter.usuario u
        LEFT JOIN t.tipoTerapia tt
        LEFT JOIN t.estado es
        WHERE ter.id = :terapeutaId
        """)
    Page<TratamientoDTO> findByTerapeutaIdProjected(@Param("terapeutaId") Long terapeutaId, Pageable pageable);
}
