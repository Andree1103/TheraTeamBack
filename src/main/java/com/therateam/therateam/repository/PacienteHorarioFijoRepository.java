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
     * Todos los horarios activos de la clínica, en una sola consulta.
     *
     * Existe para no pedir la lista paciente por paciente al exportar: con cien pacientes eso
     * son cien viajes. El JOIN FETCH trae paciente, terapeuta (con su usuario, que es donde
     * vive el nombre) y tipo de terapia ya resueltos, que es todo lo que el resumen muestra.
     */
    @Query("""
        SELECT h FROM PacienteHorarioFijo h
        JOIN FETCH h.paciente p
        LEFT JOIN FETCH p.sede
        JOIN FETCH h.terapeuta t
        LEFT JOIN FETCH t.usuario
        LEFT JOIN FETCH h.tipoTerapia
        WHERE h.activo = true
        ORDER BY p.apellido, p.nombre, h.diaSemana, h.horaInicio
        """)
    List<PacienteHorarioFijo> todosActivos();

    @Query("DELETE FROM PacienteHorarioFijo h WHERE h.paciente.id = :pacienteId")
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    void borrarDelPaciente(@Param("pacienteId") Long pacienteId);
}
