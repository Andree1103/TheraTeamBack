package com.therateam.therateam.controller;

import com.therateam.therateam.model.TerapeutaExcepcion;
import com.therateam.therateam.service.TerapeutaExcepcionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/terapeuta-excepciones")
@RequiredArgsConstructor
public class TerapeutaExcepcionController {

    private final TerapeutaExcepcionService service;

    @GetMapping
    public List<TerapeutaExcepcion> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<TerapeutaExcepcion> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/terapeuta/{terapeutaId}")
    public List<TerapeutaExcepcion> getByTerapeuta(@PathVariable Long terapeutaId) {
        return service.findByTerapeuta(terapeutaId);
    }

    @PostMapping
    public ResponseEntity<TerapeutaExcepcion> create(@RequestBody TerapeutaExcepcion excepcion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(excepcion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TerapeutaExcepcion> update(@PathVariable Long id, @RequestBody TerapeutaExcepcion excepcion) {
        return service.update(id, excepcion).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
