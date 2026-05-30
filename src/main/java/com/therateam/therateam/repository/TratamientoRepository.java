package com.therateam.therateam.repository;
import com.therateam.therateam.model.Tratamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface TratamientoRepository extends JpaRepository<Tratamiento, Long> {
    List<Tratamiento> findByPacienteId(Long pacienteId);
    List<Tratamiento> findByTerapeutaId(Long terapeutaId);
    List<Tratamiento> findByPacienteIdAndTerapeutaIdAndTipoTerapiaId(Long pacienteId, Long terapeutaId, Long tipoTerapiaId);
}
