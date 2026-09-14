package com.therateam.therateam.repository;

import com.therateam.therateam.model.Archivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchivoRepository extends JpaRepository<Archivo, Long> {
    List<Archivo> findByEntidadTipoAndEntidadIdOrderByIdAsc(String entidadTipo, Long entidadId);

    long countByEntidadTipoAndEntidadId(String entidadTipo, Long entidadId);
}
