package com.therateam.therateam.repository;

import com.therateam.therateam.dto.TerapeutaDTO;
import com.therateam.therateam.model.Terapeuta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TerapeutaRepository extends JpaRepository<Terapeuta, Long> {

    @Query("SELECT t FROM Terapeuta t WHERE LOWER(CONCAT(t.usuario.nombre, ' ', t.usuario.apellido)) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Terapeuta> findByNombreCompleto(@Param("nombre") String nombre);

    Optional<Terapeuta> findByUsuarioId(Long usuarioId);

    /**
     * Proyección liviana para listados: solo trae usuario/tipoTerapeuta/area (relaciones a-uno).
     * Las especialidades (colección) se cargan aparte en batch para no multiplicar filas.
     */
    /** Cada parámetro nulo/vacío no restringe — filtros por campo separado (nombre, CMP, área, estado). */
    @Query("""
        SELECT new com.therateam.therateam.dto.TerapeutaDTO(
            t.id, u.nombre, u.apellido, u.email, t.cmp, t.telefono, t.fotoUrl, t.horarioDescripcion, t.activo,
            new com.therateam.therateam.dto.TerapeutaDTO$TipoInfo(tt.id, tt.key, tt.nombre),
            new com.therateam.therateam.dto.TerapeutaDTO$AreaInfo(a.id, a.key, a.nombre)
        )
        FROM Terapeuta t
        LEFT JOIN t.usuario u
        LEFT JOIN t.tipoTerapeuta tt
        LEFT JOIN t.area a
        WHERE (CAST(:nombre AS string) IS NULL
               OR LOWER(CONCAT(u.nombre, ' ', u.apellido)) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
          AND (CAST(:cmp AS string) IS NULL OR LOWER(t.cmp) LIKE LOWER(CONCAT('%', CAST(:cmp AS string), '%')))
          AND (CAST(:areaId AS long) IS NULL OR a.id = :areaId)
          AND (CAST(:activo AS boolean) IS NULL OR t.activo = :activo)
        """)
    Page<TerapeutaDTO> findAllProjected(@Param("nombre") String nombre, @Param("cmp") String cmp,
                                         @Param("areaId") Long areaId, @Param("activo") Boolean activo,
                                         Pageable pageable);

    /** Especialidades de un lote de terapeutas, en una sola query (para no hacer N+1 por página). */
    @Query("""
        SELECT t.id AS terapeutaId, e.id AS id, e.key AS key, e.nombre AS nombre
        FROM Terapeuta t JOIN t.especialidades e
        WHERE t.id IN :terapeutaIds
        """)
    List<Object[]> findEspecialidadesByTerapeutaIds(@Param("terapeutaIds") List<Long> terapeutaIds);
}
