package com.therateam.therateam.service;

import com.therateam.therateam.model.Producto;
import com.therateam.therateam.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Alta, edición y reposición de stock del catálogo de productos. */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository repository;

    public List<Producto> findAll() { return repository.findAllByOrderByNombreAsc(); }

    /** Lo que se ofrece al vender: solo productos vigentes. */
    public List<Producto> findVendibles() { return repository.findByActivoTrueOrderByNombreAsc(); }

    public Optional<Producto> findById(Long id) { return repository.findById(id); }

    @Transactional
    public Producto save(Producto p) {
        validar(p);
        return repository.save(p);
    }

    @Transactional
    public Optional<Producto> update(Long id, Producto data) {
        return repository.findById(id).map(existente -> {
            validar(data);
            existente.setNombre(data.getNombre().trim());
            existente.setDescripcion(data.getDescripcion());
            existente.setPrecio(data.getPrecio());
            if (data.getStock() != null) existente.setStock(Math.max(0, data.getStock()));
            if (data.getActivo() != null) existente.setActivo(data.getActivo());
            return repository.save(existente);
        });
    }

    /** Reposición: suma unidades al stock sin tocar precio ni nombre. */
    @Transactional
    public Optional<Producto> reponer(Long id, int unidades) {
        if (unidades <= 0) throw new IllegalArgumentException("Las unidades a reponer deben ser mayores a cero.");
        return repository.findById(id).map(p -> {
            int actual = p.getStock() != null ? p.getStock() : 0;
            p.setStock(actual + unidades);
            return repository.save(p);
        });
    }

    /**
     * No se borra: se desactiva. Un producto ya vendido está referenciado por venta_items, y
     * borrarlo dejaría el historial de ventas sin poder decir qué se vendió.
     */
    @Transactional
    public boolean desactivar(Long id) {
        return repository.findById(id).map(p -> {
            p.setActivo(false);
            repository.save(p);
            return true;
        }).orElse(false);
    }

    private void validar(Producto p) {
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
        }
        if (p.getPrecio() == null || p.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo.");
        }
    }
}
