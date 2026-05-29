package com.therateam.therateam.repository;
import com.therateam.therateam.model.Sede;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface SedeRepository extends JpaRepository<Sede, Long> {}
