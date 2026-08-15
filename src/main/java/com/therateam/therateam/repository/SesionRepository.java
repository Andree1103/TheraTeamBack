package com.therateam.therateam.repository;

import com.therateam.therateam.model.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesionRepository extends JpaRepository<Sesion, Long> {
    List<Sesion> findByTratamientoId(Long tratamientoId);
    List<Sesion> findByTratamientoIdOrderByNumeroAsc(Long tratamientoId);

    @Query("SELECT s FROM Sesion s LEFT JOIN FETCH s.citaActiva WHERE s.tratamiento.id = :tratamientoId ORDER BY s.numero ASC")
    List<Sesion> findByTratamientoIdWithCita(@Param("tratamientoId") Long tratamientoId);

    /** Desvincula (no borra) la sesión del paquete al eliminar su cita activa — la sesión sigue existiendo, solo queda sin cita. */
    @Modifying
    @Query("UPDATE Sesion s SET s.citaActiva = null WHERE s.citaActiva.id = :citaId")
    void desvincularCitaActiva(@Param("citaId") Long citaId);
}
