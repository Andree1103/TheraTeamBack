package com.therateam.therateam.repository;

import com.therateam.therateam.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("SELECT u FROM Usuario u WHERE u.id NOT IN " +
           "(SELECT t.usuario.id FROM Terapeuta t WHERE t.usuario IS NOT NULL)")
    List<Usuario> findUsuariosLibres();

    Optional<Usuario> findByEmail(String email);

    /** Filtros por campo separado server-side; cualquier parámetro nulo/vacío no restringe. */
    @Query("""
        SELECT u FROM Usuario u
        WHERE (CAST(:nombre AS string) IS NULL
               OR LOWER(CONCAT(u.nombre, ' ', u.apellido)) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
          AND (CAST(:correo AS string) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:correo AS string), '%')))
          AND (CAST(:rolId AS long) IS NULL OR u.rol.id = :rolId)
          AND (CAST(:activo AS boolean) IS NULL OR u.activo = :activo)
        """)
    Page<Usuario> buscarPaged(@Param("nombre") String nombre, @Param("correo") String correo,
                               @Param("rolId") Long rolId, @Param("activo") Boolean activo, Pageable pageable);
}
