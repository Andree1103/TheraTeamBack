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
     * dias antes. Las que todavia no tienen cita no tienen fecha con que ordenarse: van al final.
     *
     * Este es ademas el orden que manda: renumerarPorFechaDeCita lo recorre y reescribe el numero
     * de cada sesion, para que «sesion 3 de 5» signifique la tercera que pasa y no la tercera que
     * se creo. Con eso el orden por numero de findByTratamientoIdWithCita —el que reparte el
     * dinero entre las sesiones, ver PagoService#repartirEntreSesiones— pasa a ser tambien el
     * cronologico, que es lo que se espera: un adelanto cubre primero las sesiones mas proximas.
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

    /**
     * Por cada paquete: cuantas filas de sesion tiene y cuantas de ellas tienen cita.
     *
     * La diferencia contra totalSesiones es lo que el paquete cobra y todavia no ha puesto en el
     * calendario. Pasaba desapercibido: al crear el paquete, cada fecha que choca con otra cita
     * del paciente se pierde con un aviso de unos segundos y el paquete se queda corto para
     * siempre. Agrupado para que el listado no haga una consulta por fila.
     * Devuelve [tratamientoId, filas, conCita].
     */
    @Query("""
            SELECT s.tratamiento.id, COUNT(s),
                   SUM(CASE WHEN s.citaActiva IS NOT NULL THEN 1 ELSE 0 END)
              FROM Sesion s
             WHERE s.tratamiento.id IN :ids
             GROUP BY s.tratamiento.id
            """)
    List<Object[]> contarSesionesPorTratamiento(@Param("ids") java.util.Collection<Long> ids);

    /** Desvincula (no borra) la sesión del paquete al eliminar su cita activa — la sesión sigue existiendo, solo queda sin cita. */
    @Modifying
    @Query("UPDATE Sesion s SET s.citaActiva = null WHERE s.citaActiva.id = :citaId")
    void desvincularCitaActiva(@Param("citaId") Long citaId);

    /**
     * Vuelve a numerar las sesiones de un paquete siguiendo la fecha de su cita.
     *
     * Hasta ahora el numero se quedaba fijo desde que se creo la sesion, asi que un paquete que
     * se modificaba —una cita reprogramada, una sesion creada despues, citas sueltas absorbidas—
     * quedaba listado por fecha pero numerado por antiguedad: 1, 2, 5, 3, 4. El numero es lo que
     * se lee en la cita ("sesion 3 de 5") y en el historial, y leer que la quinta sesion va
     * tercera no lo entiende nadie.
     *
     * Va en dos pasos porque (tratamiento_id, numero) es UNIQUE y no es diferible: si se
     * asignara 1..N de una vez, a media sentencia dos filas compartirian numero y Postgres
     * abortaria. Pasarlos antes a negativo deja el rango positivo libre; es el mismo truco que
     * se usa para reordenar cualquier columna con indice unico.
     *
     * Las sesiones sin cita no tienen fecha con que ordenarse: van al final, en el orden en que
     * se crearon, que es el que ya trae findByTratamientoIdParaMostrar.
     *
     * Se hace sobre las entidades y no con un UPDATE nativo a proposito: asi las instancias que
     * el resto de la operacion ya tiene en la mano quedan con el numero nuevo, y la respuesta de
     * «crear cita» no sale diciendo un numero que en la base ya es otro.
     *
     * Debe correr dentro de una transaccion.
     */
    default void renumerarPorFechaDeCita(Long tratamientoId) {
        if (tratamientoId == null) return;
        List<Sesion> enOrden = findByTratamientoIdParaMostrar(tratamientoId);
        if (enOrden.isEmpty()) return;

        boolean yaEstaBien = true;
        for (int i = 0; i < enOrden.size(); i++) {
            if (!Integer.valueOf(i + 1).equals(enOrden.get(i).getNumero())) { yaEstaBien = false; break; }
        }
        if (yaEstaBien) return;

        for (int i = 0; i < enOrden.size(); i++) enOrden.get(i).setNumero(-(i + 1));
        saveAll(enOrden);
        flush();
        for (int i = 0; i < enOrden.size(); i++) enOrden.get(i).setNumero(i + 1);
        saveAll(enOrden);
        flush();
    }
}
