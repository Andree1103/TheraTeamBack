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
     * Los horarios de OTROS pacientes en la misma casilla (terapeuta, día y hora). Es lo que
     * permite avisar de que dos pacientes van a chocar antes de guardar.
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

    @Query("DELETE FROM PacienteHorarioFijo h WHERE h.paciente.id = :pacienteId")
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    void borrarDelPaciente(@Param("pacienteId") Long pacienteId);
}
