package com.therateam.therateam.repository;
import com.therateam.therateam.model.CitaHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface CitaHistorialRepository extends JpaRepository<CitaHistorial, Long> {
    List<CitaHistorial> findByCitaIdOrderByCreatedAtAsc(Long citaId);

    /** Cuantas anotaciones del historial dejo este usuario. Se mira antes de borrar la
     *  cuenta de un paciente: si toco algo, la cuenta se queda. */
    long countByUsuarioId(Long usuarioId);
}
