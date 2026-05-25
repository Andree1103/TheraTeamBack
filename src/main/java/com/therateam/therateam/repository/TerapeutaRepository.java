package com.therateam.therateam.repository;

import com.therateam.therateam.model.Terapeuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerapeutaRepository extends JpaRepository<Terapeuta, Long> {
}
