package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.CatTurno;
import com.therateam.therateam.service.CatTurnoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-turnos")
@RequiredArgsConstructor
public class CatTurnoController {

    private final CatTurnoService service;

    @GetMapping
    public List<CatTurno> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatTurno> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    @PostMapping
    public ResponseEntity<CatTurno> create(@RequestBody CatTurno turno) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(turno));
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<CatTurno> update(@PathVariable Long id, @RequestBody CatTurno turno) {
        return service.update(id, turno).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
