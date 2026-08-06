package com.therateam.therateam.repository;

import com.therateam.therateam.model.CierreCaja;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    Optional<CierreCaja> findByFechaAndTurno(LocalDate fecha, Integer turno);

    /**
     * El cierre más reciente ANTERIOR a (fecha, turno) — de ahí sale el saldo inicial del turno
     * (el turno 2 arrastra del turno 1 del mismo día; el turno 1 arrastra del último turno del
     * día anterior que haya cerrado).
     */
    @Query("""
        SELECT c FROM CierreCaja c
        WHERE c.fecha < :fecha OR (c.fecha = :fecha AND c.turno < :turno)
        ORDER BY c.fecha DESC, c.turno DESC
        """)
    List<CierreCaja> findAnteriores(@Param("fecha") LocalDate fecha, @Param("turno") Integer turno, Pageable pageable);

    List<CierreCaja> findByFechaBetweenOrderByFechaDescTurnoDesc(LocalDate desde, LocalDate hasta);
}
