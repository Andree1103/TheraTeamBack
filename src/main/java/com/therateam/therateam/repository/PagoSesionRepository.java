package com.therateam.therateam.repository;
import com.therateam.therateam.model.PagoSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface PagoSesionRepository extends JpaRepository<PagoSesion, Long> {
    List<PagoSesion> findByPagoId(Long pagoId);
    List<PagoSesion> findBySesionId(Long sesionId);
}
