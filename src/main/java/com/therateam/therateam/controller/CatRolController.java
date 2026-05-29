package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatRol;
import com.therateam.therateam.service.CatRolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-roles")
@RequiredArgsConstructor
public class CatRolController {

    private final CatRolService service;

    @GetMapping
    public List<CatRol> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatRol> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatRol> create(@RequestBody CatRol rol) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(rol));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatRol> update(@PathVariable Long id, @RequestBody CatRol rol) {
        return service.update(id, rol).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
