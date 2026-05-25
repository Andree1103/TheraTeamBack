package com.therateam.therateam.controller;

import com.therateam.therateam.model.Sesion;
import com.therateam.therateam.service.SesionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sesiones")
@RequiredArgsConstructor
public class SesionController {

    private final SesionService service;

    @GetMapping
    public List<Sesion> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sesion> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Sesion> create(@RequestBody Sesion sesion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(sesion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sesion> update(@PathVariable Long id, @RequestBody Sesion sesion) {
        return service.update(id, sesion)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
