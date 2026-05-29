package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatEstadoTratamiento;
import com.therateam.therateam.service.CatEstadoTratamientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-estados-tratamiento")
@RequiredArgsConstructor
public class CatEstadoTratamientoController {

    private final CatEstadoTratamientoService service;

    @GetMapping
    public List<CatEstadoTratamiento> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatEstadoTratamiento> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatEstadoTratamiento> create(@RequestBody CatEstadoTratamiento estado) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(estado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatEstadoTratamiento> update(@PathVariable Long id, @RequestBody CatEstadoTratamiento estado) {
        return service.update(id, estado).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
