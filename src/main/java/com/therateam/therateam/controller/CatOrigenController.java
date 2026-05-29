package com.therateam.therateam.controller;

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

    @PostMapping
    public ResponseEntity<CatOrigen> create(@RequestBody CatOrigen origen) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(origen));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatOrigen> update(@PathVariable Long id, @RequestBody CatOrigen origen) {
        return service.update(id, origen).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
