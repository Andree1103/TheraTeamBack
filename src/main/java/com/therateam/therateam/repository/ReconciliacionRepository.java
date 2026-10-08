package com.therateam.therateam.repository;

import com.therateam.therateam.model.Reconciliacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliacionRepository extends JpaRepository<Reconciliacion, Long> {

    List<Reconciliacion> findAllByOrderByMomentoDesc(Pageable pageable);

    /** La ultima vez que NO cuadro. Vacio si nunca ha fallado. */
    @Query("SELECT r FROM Reconciliacion r WHERE r.cuadra = false ORDER BY r.momento DESC")
    List<Reconciliacion> ultimosFallos(Pageable pageable);
}
