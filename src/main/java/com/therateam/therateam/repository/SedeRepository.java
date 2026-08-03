package com.therateam.therateam.repository;
import com.therateam.therateam.model.Sede;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SedeRepository extends JpaRepository<Sede, Long> {
    /** La sede "por defecto" para nuevos pacientes/usuarios: la más antigua activa (típicamente la Sede Principal, creada primero). */
    Optional<Sede> findFirstByActivoTrueOrderByIdAsc();
}
