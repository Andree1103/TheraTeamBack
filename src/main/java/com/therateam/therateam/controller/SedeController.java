package com.therateam.therateam.controller;

import com.therateam.therateam.model.Sede;
import com.therateam.therateam.service.SedeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sedes")
@RequiredArgsConstructor
public class SedeController {

    private final SedeService service;

    @GetMapping
    public List<Sede> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Sede> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Sede> create(@RequestBody Sede sede) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(sede));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sede> update(@PathVariable Long id, @RequestBody Sede sede) {
        return service.update(id, sede).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
