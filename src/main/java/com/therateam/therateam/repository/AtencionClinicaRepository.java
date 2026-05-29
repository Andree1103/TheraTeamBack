package com.therateam.therateam.repository;
import com.therateam.therateam.model.AtencionClinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface AtencionClinicaRepository extends JpaRepository<AtencionClinica, Long> {
    Optional<AtencionClinica> findByCitaId(Long citaId);
}
