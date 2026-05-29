package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatTipoTerapeuta;
import com.therateam.therateam.service.CatTipoTerapeutaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-tipos-terapeuta")
@RequiredArgsConstructor
public class CatTipoTerapeutaController {

    private final CatTipoTerapeutaService service;

    @GetMapping
    public List<CatTipoTerapeuta> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatTipoTerapeuta> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatTipoTerapeuta> create(@RequestBody CatTipoTerapeuta tipo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(tipo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatTipoTerapeuta> update(@PathVariable Long id, @RequestBody CatTipoTerapeuta tipo) {
        return service.update(id, tipo).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
