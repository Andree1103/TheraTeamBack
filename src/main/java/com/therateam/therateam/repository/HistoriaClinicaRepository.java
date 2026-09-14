package com.therateam.therateam.repository;

import com.therateam.therateam.model.HistoriaClinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistoriaClinicaRepository extends JpaRepository<HistoriaClinica, Long> {
    List<HistoriaClinica> findByPacienteIdOrderByIdAsc(Long pacienteId);

    Optional<HistoriaClinica> findByPacienteIdAndPlantillaId(Long pacienteId, Long plantillaId);
}
