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
     * Las cuentas que hoy pueden pagar por este paciente.
     *
     * Son varias desde el 17-11-2026: a la misma persona la puede pagar su hija una semana y su
     * hijo la siguiente, y antes la segunda cita se caía con "ya está amarrado a otra cuenta".
     * Cuál paga cada atención lo decide la cita, no esta tabla; esto solo dice quiénes PUEDEN.
     *
     * De la más nueva a la más vieja, que es el orden en que conviene ofrecerlas: lo último que
     * se hizo es lo más probable que se repita.
     */
    @Query("""
        SELECT a FROM CuentaCreditoPaciente a
          JOIN FETCH a.cuenta cu
          JOIN FETCH cu.titular
         WHERE a.paciente.id = :pacienteId
           AND a.activo = true
         ORDER BY a.id DESC
        """)
    List<CuentaCreditoPaciente> amarresVigentesDe(@Param("pacienteId") Long pacienteId);

    /** El par exacto, activo o no. Lo necesita el service antes de cargar una cita: la FK
     *  compuesta de citas exige que el par exista, aunque el amarre ya esté desactivado. */
    Optional<CuentaCreditoPaciente> findByCuentaIdAndPacienteId(Long cuentaId, Long pacienteId);

    /** Cuántas filas de aquí cuelgan de este paciente. Se usa antes de borrarlo: la FK no tiene
     *  ON DELETE, así que una sola basta para que el borrado falle. */
    long countByPacienteId(Long pacienteId);
}
