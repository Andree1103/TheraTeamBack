package com.therateam.therateam.repository;
import com.therateam.therateam.model.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {
    Optional<Configuracion> findBySedeIsNullAndClave(String clave);
    List<Configuracion> findBySedeIsNull();
}
