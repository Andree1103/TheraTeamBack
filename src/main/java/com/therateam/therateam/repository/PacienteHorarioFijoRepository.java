package com.therateam.therateam.repository;

import com.therateam.therateam.model.PacienteHorarioFijo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PacienteHorarioFijoRepository extends JpaRepository<PacienteHorarioFijo, Long> {

    /**
     * Los horarios de un paciente, ordenados como se leen: primero por día de la semana y
     * dentro del día por hora.
     *
     * @Query explícito en vez de derivarlo del nombre, por lo mismo que en las otras entidades
     * con un getPacienteId() transitorio: Spring Data lo confunde con un atributo persistido.
     */
    @Query("""
        SELECT h FROM PacienteHorarioFijo h
        WHERE h.paciente.id = :pacienteId
        ORDER BY h.diaSemana, h.horaInicio
        """)
    List<PacienteHorarioFijo> delPaciente(@Param("pacienteId") Long pacienteId);

    /** Para mirar la semana de un terapeuta: quién tiene horario fijo con él. */
    @Query("""
        SELECT h FROM PacienteHorarioFijo h
        WHERE h.terapeuta.id = :terapeutaId AND h.activo = true
        ORDER BY h.diaSemana, h.horaInicio
        """)
    List<PacienteHorarioFijo> delTerapeuta(@Param("terapeutaId") Long terapeutaId);

    /**
     * Los horarios de OTROS pacientes en la misma casilla (terapeuta, día y hora de inicio).
     * Es lo que permite avisar de que dos pacientes van a chocar antes de guardar.
     *
     * Se compara por hora de INICIO y no por solapamiento de rangos a propósito: la duración del
     * horario fijo es un dato informativo, no reserva nada. Hacerla decidir quién puede quedarse
     * con una casilla le daría un peso que no tiene y generaría bloqueos que la agenda —que es
     * quien de verdad valida el cupo— no aplicaría.
     *
     * Excluye al propio paciente porque al editar se reemplaza su lista completa: sus propias
     * líneas están a punto de borrarse y contarlas sería chocar consigo mismo.
     */
    @Query("""
        SELECT h FROM PacienteHorarioFijo h
        JOIN FETCH h.paciente p
        WHERE h.terapeuta.id = :terapeutaId
          AND h.diaSemana = :diaSemana
          AND h.horaInicio = :horaInicio
          AND h.activo = true
          AND p.id <> :excluirPacienteId
        """)
    List<PacienteHorarioFijo> enLaMismaCasilla(@Param("terapeutaId") Long terapeutaId,
                                               @Param("diaSemana") Integer diaSemana,
                                               @Param("horaInicio") java.time.LocalTime horaInicio,
                                               @Param("excluirPacienteId") Long excluirPacienteId);

    /**
     * Los horarios activos de los pacientes que cumplen los MISMOS filtros que el listado de
     * pacientes, en una sola consulta.
     *
     * Existe por dos razones. Una: pedir la lista paciente por paciente al exportar son cien
     * viajes con cien pacientes. Dos: el recorte va aquí y no en el navegador, porque traerse
     * el cuadro entero para tirar la mayor parte es cargar memoria por nada — el mismo criterio
     * que en la agenda.
     *
     * Las condiciones son copia literal de PacienteRepository.buscarPaged: si divergen, el Excel
     * de horarios dejaría de coincidir con el de pacientes, que es justo lo que se busca evitar.
     *
     * El JOIN FETCH trae paciente, terapeuta (con su usuario, que es donde vive el nombre) y
     * tipo de terapia ya resueltos, que es todo lo que el resumen muestra.
     */
    @Query("""
        SELECT h FROM PacienteHorarioFijo h
        JOIN FETCH h.paciente p
        LEFT JOIN FETCH p.sede
        JOIN FETCH h.terapeuta t
        LEFT JOIN FETCH t.usuario
        LEFT JOIN FETCH h.tipoTerapia
        WHERE h.activo = true
          AND (CAST(:nombre AS string) IS NULL
               OR LOWER(CONCAT(p.nombre, ' ', p.apellido)) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
          AND (CAST(:dni AS string) IS NULL OR LOWER(p.dni) LIKE LOWER(CONCAT('%', CAST(:dni AS string), '%')))
          AND (CAST(:correo AS string) IS NULL OR LOWER(p.correo) LIKE LOWER(CONCAT('%', CAST(:correo AS string), '%')))
          AND (CAST(:sedeId AS long) IS NULL OR p.sede.id = :sedeId)
          AND (CAST(:activo AS boolean) IS NULL OR p.activo = :activo)
          AND (CAST(:terapeutaId AS long) IS NULL OR EXISTS (
                SELECT 1 FROM Cita c WHERE c.paciente = p AND c.terapeuta.id = :terapeutaId
              ))
          AND (CAST(:creadoDesde AS timestamp) IS NULL OR p.createdAt >= :creadoDesde)
          AND (CAST(:creadoHasta AS timestamp) IS NULL OR p.createdAt <= :creadoHasta)
        ORDER BY p.apellido, p.nombre, h.diaSemana, h.horaInicio
        """)
    List<PacienteHorarioFijo> buscar(@Param("nombre") String nombre, @Param("dni") String dni,
                                     @Param("correo") String correo, @Param("sedeId") Long sedeId,
                                     @Param("activo") Boolean activo,
                                     @Param("terapeutaId") Long terapeutaId,
                                     @Param("creadoDesde") java.time.LocalDateTime creadoDesde,
                                     @Param("creadoHasta") java.time.LocalDateTime creadoHasta);

    @Query("DELETE FROM PacienteHorarioFijo h WHERE h.paciente.id = :pacienteId")
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    void borrarDelPaciente(@Param("pacienteId") Long pacienteId);
}
