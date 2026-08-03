package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.CatOrigen;
import com.therateam.therateam.service.CatOrigenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-origenes")
@RequiredArgsConstructor
public class CatOrigenController {

    private final CatOrigenService service;

    @GetMapping
    public List<CatOrigen> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatOrigen> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    @PostMapping
    public ResponseEntity<CatOrigen> create(@RequestBody CatOrigen origen) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(origen));
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<CatOrigen> update(@PathVariable Long id, @RequestBody CatOrigen origen) {
        return service.update(id, origen).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
