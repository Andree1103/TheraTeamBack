package com.therateam.therateam.repository;

import com.therateam.therateam.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("SELECT u FROM Usuario u WHERE u.id NOT IN " +
           "(SELECT t.usuario.id FROM Terapeuta t WHERE t.usuario IS NOT NULL)")
    List<Usuario> findUsuariosLibres();
}
