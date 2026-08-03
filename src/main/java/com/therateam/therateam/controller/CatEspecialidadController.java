package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.CatEspecialidad;
import com.therateam.therateam.service.CatEspecialidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-especialidades")
@RequiredArgsConstructor
public class CatEspecialidadController {

    private final CatEspecialidadService service;

    @GetMapping
    public List<CatEspecialidad> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatEspecialidad> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    @PostMapping
    public ResponseEntity<CatEspecialidad> create(@RequestBody CatEspecialidad especialidad) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(especialidad));
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<CatEspecialidad> update(@PathVariable Long id, @RequestBody CatEspecialidad especialidad) {
        return service.update(id, especialidad).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
