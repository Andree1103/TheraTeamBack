package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatEstadoSesion;
import com.therateam.therateam.service.CatEstadoSesionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-estados-sesion")
@RequiredArgsConstructor
public class CatEstadoSesionController {

    private final CatEstadoSesionService service;

    @GetMapping
    public List<CatEstadoSesion> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatEstadoSesion> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatEstadoSesion> create(@RequestBody CatEstadoSesion estado) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(estado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatEstadoSesion> update(@PathVariable Long id, @RequestBody CatEstadoSesion estado) {
        return service.update(id, estado).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
