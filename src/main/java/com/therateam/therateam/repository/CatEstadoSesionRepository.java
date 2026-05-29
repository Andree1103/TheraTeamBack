package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatEstadoSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface CatEstadoSesionRepository extends JpaRepository<CatEstadoSesion, Long> {
    Optional<CatEstadoSesion> findByKey(String key);
}
