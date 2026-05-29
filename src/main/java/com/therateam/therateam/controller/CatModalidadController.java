package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatModalidad;
import com.therateam.therateam.service.CatModalidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-modalidades")
@RequiredArgsConstructor
public class CatModalidadController {

    private final CatModalidadService service;

    @GetMapping
    public List<CatModalidad> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatModalidad> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatModalidad> create(@RequestBody CatModalidad modalidad) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(modalidad));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatModalidad> update(@PathVariable Long id, @RequestBody CatModalidad modalidad) {
        return service.update(id, modalidad).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
