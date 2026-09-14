package com.therateam.therateam.repository;

import com.therateam.therateam.model.HistoriaClinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistoriaClinicaRepository extends JpaRepository<HistoriaClinica, Long> {
    // El guion bajo es obligatorio: la entidad tiene un getter de conveniencia getPacienteId(),
    // y sin el separador Spring Data lo toma por un atributo propio en vez de navegar la
    // relacion, y la consulta revienta con UnknownPathException.
    List<HistoriaClinica> findByPaciente_IdOrderByIdAsc(Long pacienteId);

    Optional<HistoriaClinica> findByPaciente_IdAndPlantilla_Id(Long pacienteId, Long plantillaId);
}
