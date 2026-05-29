package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatTipoTerapeuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface CatTipoTerapeutaRepository extends JpaRepository<CatTipoTerapeuta, Long> {}
