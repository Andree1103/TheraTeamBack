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

    /**
     * Las sesiones de un paquete en el orden en que van a ocurrir — el de la pantalla del paquete.
     *
     * Nacen numeradas 1..N con las fechas que se eligieron al abrirlo, pero reprogramar una cita
     * le mueve la fecha sin moverle el numero, asi que el detalle quedaba listado en el orden en
     * que se planeo y no en el que pasa: la sesion 4 aparecia debajo de la 3 aunque fuera tres
     * dias antes. El numero no se recalcula: es el nombre de la sesion, y asi se la llama en la
     * cita ("sesion 3 de 5") y en el historial. Lo que pasa a seguir a la fecha es el orden de
     * las filas. Las que todavia no tienen cita no tienen fecha con que ordenarse: van al final.
     *
     * Aparte a proposito de findByTratamientoIdWithCita: ese orden por numero es el que reparte
     * el dinero entre las sesiones (PagoService#repartirEntreSesiones y su inverso), y moverlo
     * ahi cambiaria a que sesion se le aplica un adelanto.
     */
    @Query("""
            SELECT s FROM Sesion s
            LEFT JOIN FETCH s.citaActiva c
            WHERE s.tratamiento.id = :tratamientoId
            ORDER BY CASE WHEN c.fechaInicio IS NULL THEN 1 ELSE 0 END,
                     c.fechaInicio ASC,
                     s.numero ASC
            """)
    List<Sesion> findByTratamientoIdParaMostrar(@Param("tratamientoId") Long tratamientoId);

    /** Desvincula (no borra) la sesión del paquete al eliminar su cita activa — la sesión sigue existiendo, solo queda sin cita. */
    @Modifying
    @Query("UPDATE Sesion s SET s.citaActiva = null WHERE s.citaActiva.id = :citaId")
    void desvincularCitaActiva(@Param("citaId") Long citaId);
}
