package com.therateam.therateam.repository;
import com.therateam.therateam.model.AtencionClinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface AtencionClinicaRepository extends JpaRepository<AtencionClinica, Long> {
    // @Query explícito en vez de derivarlo del nombre: la entidad ahora expone un getCitaId()
    // transitorio (para el JSON del front) que Spring Data confundía con un atributo persistido
    // llamado "citaId" al intentar resolver "findByCitaId" por convención.
    @Query("SELECT a FROM AtencionClinica a WHERE a.cita.id = :citaId")
    Optional<AtencionClinica> findByCitaId(@Param("citaId") Long citaId);
}
