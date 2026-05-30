package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatModalidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatModalidadRepository extends JpaRepository<CatModalidad, Long> {
    Optional<CatModalidad> findByKey(String key);
}
