package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatEspecialidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface CatEspecialidadRepository extends JpaRepository<CatEspecialidad, Long> {}
