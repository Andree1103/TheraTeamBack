package com.therateam.therateam.repository;

import com.therateam.therateam.dto.SaldoMovimientoDTO;
import com.therateam.therateam.model.SaldoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaldoMovimientoRepository extends JpaRepository<SaldoMovimiento, Long> {

    /** Historial de un paciente, del movimiento más reciente al más antiguo. */
    @Query("""
        SELECT new com.therateam.therateam.dto.SaldoMovimientoDTO(
            m.id, p.id, m.monto, m.saldoResultante, m.motivo,
            CONCAT(u.nombre, ' ', u.apellido), c.id, pg.id, m.fecha)
        FROM SaldoMovimiento m
        JOIN m.paciente p
        LEFT JOIN m.terapeuta t
        LEFT JOIN t.usuario u
        LEFT JOIN m.cita c
        LEFT JOIN m.pago pg
        WHERE p.id = :pacienteId
        ORDER BY m.fecha DESC, m.id DESC
        """)
    List<SaldoMovimientoDTO> historialDe(@Param("pacienteId") Long pacienteId);

    /** Lo mismo para varios pacientes de una sola consulta: el reporte de Adelantos se queda
     *  con el primero de cada uno y así evita una consulta por fila. */
    @Query("""
        SELECT new com.therateam.therateam.dto.SaldoMovimientoDTO(
            m.id, p.id, m.monto, m.saldoResultante, m.motivo,
            CONCAT(u.nombre, ' ', u.apellido), c.id, pg.id, m.fecha)
        FROM SaldoMovimiento m
        JOIN m.paciente p
        LEFT JOIN m.terapeuta t
        LEFT JOIN t.usuario u
        LEFT JOIN m.cita c
        LEFT JOIN m.pago pg
        WHERE p.id IN :pacienteIds
        ORDER BY m.fecha DESC, m.id DESC
        """)
    List<SaldoMovimientoDTO> historialDeVarios(@Param("pacienteIds") List<Long> pacienteIds);
}
