package com.therateam.therateam.repository;

import com.therateam.therateam.model.CuentaCreditoPaciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaCreditoPacienteRepository extends JpaRepository<CuentaCreditoPaciente, Long> {

    /** Los pacientes de una cuenta: primero los vigentes, después los históricos. */
    List<CuentaCreditoPaciente> findByCuentaIdOrderByActivoDescIdAsc(Long cuentaId);

    List<CuentaCreditoPaciente> findByCuentaIdAndActivoTrueOrderByIdAsc(Long cuentaId);

    /**
     * A qué cuenta está amarrado este paciente, si a alguna.
     *
     * Devuelve a lo sumo una: el índice parcial uq_ccp_un_paciente_una_cuenta impide que un
     * paciente esté activo en dos cuentas a la vez. Esto es lo que se consulta al marcar la cita
     * para saber a qué cuenta se puede cargar, y lo que contesta "a quién está amarrado este
     * paciente" en la ficha.
     */
    @Query("""
        SELECT a FROM CuentaCreditoPaciente a
          JOIN FETCH a.cuenta cu
          JOIN FETCH cu.titular
         WHERE a.paciente.id = :pacienteId
           AND a.activo = true
        """)
    Optional<CuentaCreditoPaciente> amarreVigenteDe(@Param("pacienteId") Long pacienteId);

    /** El par exacto, activo o no. Lo necesita el service antes de cargar una cita: la FK
     *  compuesta de citas exige que el par exista, aunque el amarre ya esté desactivado. */
    Optional<CuentaCreditoPaciente> findByCuentaIdAndPacienteId(Long cuentaId, Long pacienteId);

    /** Cuántas filas de aquí cuelgan de este paciente. Se usa antes de borrarlo: la FK no tiene
     *  ON DELETE, así que una sola basta para que el borrado falle. */
    long countByPacienteId(Long pacienteId);
}
