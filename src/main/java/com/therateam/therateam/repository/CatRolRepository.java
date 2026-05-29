package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatRolRepository extends JpaRepository<CatRol, Long> {
    Optional<CatRol> findByKey(String key);
}
