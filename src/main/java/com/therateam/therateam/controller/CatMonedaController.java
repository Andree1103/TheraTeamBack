package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatMoneda;
import com.therateam.therateam.service.CatMonedaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-monedas")
@RequiredArgsConstructor
public class CatMonedaController {

    private final CatMonedaService service;

    @GetMapping
    public List<CatMoneda> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatMoneda> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatMoneda> create(@RequestBody CatMoneda moneda) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(moneda));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatMoneda> update(@PathVariable Long id, @RequestBody CatMoneda moneda) {
        return service.update(id, moneda).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
