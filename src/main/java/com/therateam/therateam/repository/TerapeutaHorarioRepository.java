package com.therateam.therateam.repository;
import com.therateam.therateam.model.TerapeutaHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface TerapeutaHorarioRepository extends JpaRepository<TerapeutaHorario, Long> {
    List<TerapeutaHorario> findByTerapeutaIdOrderByDiaSemanaAsc(Long terapeutaId);
    List<TerapeutaHorario> findByTerapeutaIdAndDiaSemanaAndActivoTrue(Long terapeutaId, Integer diaSemana);
}
