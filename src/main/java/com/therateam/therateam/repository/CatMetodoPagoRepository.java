package com.therateam.therateam.repository;
import com.therateam.therateam.model.CatMetodoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface CatMetodoPagoRepository extends JpaRepository<CatMetodoPago, Long> {}
