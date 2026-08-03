package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.TipoTerapia;
import com.therateam.therateam.service.TipoTerapiaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-terapia")
@RequiredArgsConstructor
public class TipoTerapiaController {

    private final TipoTerapiaService service;

    @GetMapping
    public List<TipoTerapia> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoTerapia> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    @PostMapping
    public ResponseEntity<TipoTerapia> create(@RequestBody TipoTerapia tipoTerapia) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(tipoTerapia));
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<TipoTerapia> update(@PathVariable Long id, @RequestBody TipoTerapia tipoTerapia) {
        return service.update(id, tipoTerapia)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
