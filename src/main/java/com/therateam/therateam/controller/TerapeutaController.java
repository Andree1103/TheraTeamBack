package com.therateam.therateam.controller;

import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.service.TerapeutaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/terapeutas")
@RequiredArgsConstructor
public class TerapeutaController {

    private final TerapeutaService service;

    @GetMapping
    public List<Terapeuta> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Terapeuta> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Terapeuta> create(@RequestBody Terapeuta terapeuta) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(terapeuta));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Terapeuta> update(@PathVariable Long id, @RequestBody Terapeuta terapeuta) {
        return service.update(id, terapeuta)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
