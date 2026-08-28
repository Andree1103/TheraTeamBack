package com.therateam.therateam.controller;

import com.therateam.therateam.dto.VentaResumenDTO;
import com.therateam.therateam.model.Producto;
import com.therateam.therateam.model.VentaItem;
import com.therateam.therateam.service.ProductoService;
import com.therateam.therateam.service.VentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Catálogo de productos y reporte de ventas. Va bajo los permisos de Pagos (igual que
 * Adelantos): quien cobra es quien vende, y así no hace falta un módulo nuevo ni reconfigurar
 * los roles existentes.
 */
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService service;
    private final VentaService ventaService;

    @GetMapping
    public List<Producto> getAll() { return service.findAll(); }

    /** Lo que alimenta el selector al registrar una venta: solo activos. */
    @GetMapping("/vendibles")
    public List<Producto> getVendibles() { return service.findVendibles(); }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_PAGOS_CREAR')")
    @PostMapping
    public ResponseEntity<Producto> create(@RequestBody Producto p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(p));
    }

    @PreAuthorize("hasAuthority('MODULO_PAGOS_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<Producto> update(@PathVariable Long id, @RequestBody Producto p) {
        return service.update(id, p).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_PAGOS_EDITAR')")
    @PostMapping("/{id}/reponer")
    public ResponseEntity<Producto> reponer(@PathVariable Long id, @RequestParam int unidades) {
        return service.reponer(id, unidades).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Desactiva en vez de borrar: las ventas ya registradas siguen apuntando a este producto. */
    @PreAuthorize("hasAuthority('MODULO_PAGOS_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        return service.desactivar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** GET /api/productos/ventas/resumen?desde=...&hasta=... — unidades y plata por producto. */
    @GetMapping("/ventas/resumen")
    public List<VentaResumenDTO> resumenVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ventaService.resumen(desde, hasta);
    }

    /** Las líneas de una venta puntual, para ver el detalle de un pago. */
    @GetMapping("/ventas/pago/{pagoId}")
    public List<VentaItem> itemsDePago(@PathVariable Long pagoId) { return ventaService.itemsDe(pagoId); }
}
