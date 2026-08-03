package com.therateam.therateam.repository;

import com.therateam.therateam.model.Paciente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PacienteRepository extends JpaRepository<Paciente, Long> {
    Optional<Paciente> findByDni(String dni);

    /**
     * Búsqueda server-side por campos separados (nombre, dni, correo, sede) + estado activo.
     * `terapeutaId` acota a pacientes con al menos una cita con ese terapeuta (para el terapeuta
     * restringido a "solo sus propias citas" — ver citasSoloPropias en Usuario); null = sin restringir.
     */
    @Query("""
        SELECT p FROM Paciente p
        WHERE (CAST(:nombre AS string) IS NULL
               OR LOWER(CONCAT(p.nombre, ' ', p.apellido)) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
          AND (CAST(:dni AS string) IS NULL OR LOWER(p.dni) LIKE LOWER(CONCAT('%', CAST(:dni AS string), '%')))
          AND (CAST(:correo AS string) IS NULL OR LOWER(p.correo) LIKE LOWER(CONCAT('%', CAST(:correo AS string), '%')))
          AND (CAST(:sedeId AS long) IS NULL OR p.sede.id = :sedeId)
          AND (CAST(:activo AS boolean) IS NULL OR p.activo = :activo)
          AND (CAST(:terapeutaId AS long) IS NULL OR EXISTS (
                SELECT 1 FROM Cita c WHERE c.paciente = p AND c.terapeuta.id = :terapeutaId
              ))
        """)
    Page<Paciente> buscarPaged(@Param("nombre") String nombre, @Param("dni") String dni,
                                @Param("correo") String correo, @Param("sedeId") Long sedeId,
                                @Param("activo") Boolean activo,
                                @Param("terapeutaId") Long terapeutaId, Pageable pageable);
}
