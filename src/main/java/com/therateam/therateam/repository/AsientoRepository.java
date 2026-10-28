package com.therateam.therateam.repository;

import com.therateam.therateam.model.Asiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AsientoRepository extends JpaRepository<Asiento, Long> {
    /** Cuantas filas de aqui cuelgan de este paciente. Se usa antes de borrarlo:
     *  la FK no tiene ON DELETE, asi que una sola basta para que el borrado falle. */
    long countByPacienteId(Long pacienteId);
}
