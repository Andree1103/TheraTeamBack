package com.therateam.therateam.repository;

import com.therateam.therateam.model.AsientoLinea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface AsientoLineaRepository extends JpaRepository<AsientoLinea, Long> {

    /**
     * Cuanta deuda de esta cita dice el libro que esta cubierta.
     *
     * Es la misma cuenta que hace el reconciliador para decidir si una cita cuadra: lo cubierto
     * menos lo liberado. Se pregunta al libro en vez de mirar cita.monto_pagado porque lo que
     * hay que mover al reprogramar es lo que el libro atribuye, no lo que la cita cree.
     */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN l.concepto = com.therateam.therateam.model.AsientoLinea.Concepto.DEUDA_CUBRE  THEN l.importe
                                 WHEN l.concepto = com.therateam.therateam.model.AsientoLinea.Concepto.DEUDA_LIBERA THEN -l.importe
                                 ELSE 0 END), 0)
          FROM AsientoLinea l WHERE l.citaId = :citaId
        """)
    BigDecimal cubiertoDeLaCita(@Param("citaId") Long citaId);
}
