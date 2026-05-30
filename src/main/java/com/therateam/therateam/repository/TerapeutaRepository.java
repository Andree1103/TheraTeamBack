package com.therateam.therateam.repository;

import com.therateam.therateam.model.Terapeuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TerapeutaRepository extends JpaRepository<Terapeuta, Long> {

    @Query("SELECT t FROM Terapeuta t WHERE LOWER(CONCAT(t.usuario.nombre, ' ', t.usuario.apellido)) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Terapeuta> findByNombreCompleto(@Param("nombre") String nombre);
}
