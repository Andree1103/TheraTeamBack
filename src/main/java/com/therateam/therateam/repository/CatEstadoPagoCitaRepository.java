package com.therateam.therateam.repository;

import com.therateam.therateam.model.CatEstadoPagoCita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatEstadoPagoCitaRepository extends JpaRepository<CatEstadoPagoCita, Long> {
    Optional<CatEstadoPagoCita> findByKey(String key);
}
