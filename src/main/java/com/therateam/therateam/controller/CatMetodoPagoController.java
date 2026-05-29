package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatMetodoPago;
import com.therateam.therateam.service.CatMetodoPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-metodos-pago")
@RequiredArgsConstructor
public class CatMetodoPagoController {

    private final CatMetodoPagoService service;

    @GetMapping
    public List<CatMetodoPago> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatMetodoPago> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatMetodoPago> create(@RequestBody CatMetodoPago metodo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(metodo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatMetodoPago> update(@PathVariable Long id, @RequestBody CatMetodoPago metodo) {
        return service.update(id, metodo).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
