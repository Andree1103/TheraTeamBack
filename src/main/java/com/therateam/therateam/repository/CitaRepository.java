package com.therateam.therateam.repository;

import com.therateam.therateam.model.Cita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {

    /**
     * Citas del terapeuta que se solapan con [fechaInicio, fechaFin), excluyendo un estado
     * (ej. CANCELADA) y opcionalmente excluyendo la propia cita (para updates).
     */
    List<Cita> findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotAndIdNot(
            Long terapeutaId, LocalDateTime fechaFin, LocalDateTime fechaInicio, String estadoKey, Long excludeId);

    List<Cita> findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNot(
            Long terapeutaId, LocalDateTime fechaFin, LocalDateTime fechaInicio, String estadoKey);
}
