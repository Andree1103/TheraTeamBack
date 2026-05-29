package com.therateam.therateam.repository;

import com.therateam.therateam.model.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesionRepository extends JpaRepository<Sesion, Long> {
    List<Sesion> findByTratamientoId(Long tratamientoId);
    List<Sesion> findByTratamientoIdOrderByNumeroAsc(Long tratamientoId);
}
