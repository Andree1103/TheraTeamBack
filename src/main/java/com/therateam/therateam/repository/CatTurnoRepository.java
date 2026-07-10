package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface CatTurnoRepository extends JpaRepository<CatTurno, Long> {}
