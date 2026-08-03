package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.PlantillaPaquete;
import com.therateam.therateam.service.PlantillaPaqueteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plantillas-paquete")
@RequiredArgsConstructor
public class PlantillaPaqueteController {

    private final PlantillaPaqueteService service;

    @GetMapping
    public List<PlantillaPaquete> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<PlantillaPaquete> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_PAQUETES_CREAR')")
    @PostMapping
    public ResponseEntity<PlantillaPaquete> create(@RequestBody PlantillaPaquete p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(p));
    }

    @PreAuthorize("hasAuthority('MODULO_PAQUETES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<PlantillaPaquete> update(@PathVariable Long id, @RequestBody PlantillaPaquete p) {
        return service.update(id, p).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_PAQUETES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
