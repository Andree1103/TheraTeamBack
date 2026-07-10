package com.therateam.therateam.repository;
import com.therateam.therateam.model.TerapeutaExcepcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
@Repository
public interface TerapeutaExcepcionRepository extends JpaRepository<TerapeutaExcepcion, Long> {
    List<TerapeutaExcepcion> findByTerapeutaIdOrderByFechaAsc(Long terapeutaId);
    List<TerapeutaExcepcion> findByTerapeutaIdAndFecha(Long terapeutaId, LocalDate fecha);
}
