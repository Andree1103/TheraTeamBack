package com.therateam.therateam.repository;

import com.therateam.therateam.model.RolModuloPermiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolModuloPermisoRepository extends JpaRepository<RolModuloPermiso, Long> {
}
