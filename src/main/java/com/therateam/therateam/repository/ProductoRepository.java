package com.therateam.therateam.repository;

import com.therateam.therateam.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Lo que el selector de venta debe ofrecer: solo lo vigente, en orden alfabético. */
    List<Producto> findByActivoTrueOrderByNombreAsc();

    List<Producto> findAllByOrderByNombreAsc();
}
