package com.therateam.therateam.repository;

import com.therateam.therateam.model.TipoTerapia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TipoTerapiaRepository extends JpaRepository<TipoTerapia, Long> {
}
