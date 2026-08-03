package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.CatArea;
import com.therateam.therateam.service.CatAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-areas")
@RequiredArgsConstructor
public class CatAreaController {

    private final CatAreaService service;

    @GetMapping
    public List<CatArea> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatArea> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    @PostMapping
    public ResponseEntity<CatArea> create(@RequestBody CatArea area) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(area));
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<CatArea> update(@PathVariable Long id, @RequestBody CatArea area) {
        return service.update(id, area).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
