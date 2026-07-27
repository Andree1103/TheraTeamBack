package com.therateam.therateam.repository;

import com.therateam.therateam.model.CierreCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    Optional<CierreCaja> findByFecha(LocalDate fecha);

    /** El cierre más reciente ANTERIOR a la fecha dada — de ahí sale el saldo inicial del día. */
    Optional<CierreCaja> findFirstByFechaLessThanOrderByFechaDesc(LocalDate fecha);

    List<CierreCaja> findByFechaBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta);
}
