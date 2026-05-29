package com.therateam.therateam.repository;
import com.therateam.therateam.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByTratamientoId(Long tratamientoId);
    List<Pago> findByPacienteId(Long pacienteId);
}
