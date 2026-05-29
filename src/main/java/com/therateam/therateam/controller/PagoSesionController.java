package com.therateam.therateam.controller;

import com.therateam.therateam.model.PagoSesion;
import com.therateam.therateam.service.PagoSesionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pago-sesiones")
@RequiredArgsConstructor
public class PagoSesionController {

    private final PagoSesionService service;

    @GetMapping
    public List<PagoSesion> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<PagoSesion> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/pago/{pagoId}")
    public List<PagoSesion> getByPago(@PathVariable Long pagoId) {
        return service.findByPago(pagoId);
    }

    @GetMapping("/sesion/{sesionId}")
    public List<PagoSesion> getBySesion(@PathVariable Long sesionId) {
        return service.findBySesion(sesionId);
    }

    @PostMapping
    public ResponseEntity<PagoSesion> create(@RequestBody PagoSesion pagoSesion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(pagoSesion));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
