package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatOrigen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface CatOrigenRepository extends JpaRepository<CatOrigen, Long> {}
