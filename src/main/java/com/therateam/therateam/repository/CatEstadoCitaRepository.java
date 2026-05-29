package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatEstadoCita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface CatEstadoCitaRepository extends JpaRepository<CatEstadoCita, Long> {
    Optional<CatEstadoCita> findByKey(String key);
}
