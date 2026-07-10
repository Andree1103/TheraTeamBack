package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatEstadoPagoCita;
import com.therateam.therateam.service.CatEstadoPagoCitaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-estados-pago-cita")
@RequiredArgsConstructor
public class CatEstadoPagoCitaController {

    private final CatEstadoPagoCitaService service;

    @GetMapping
    public List<CatEstadoPagoCita> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatEstadoPagoCita> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatEstadoPagoCita> create(@RequestBody CatEstadoPagoCita estadoPagoCita) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(estadoPagoCita));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatEstadoPagoCita> update(@PathVariable Long id, @RequestBody CatEstadoPagoCita estadoPagoCita) {
        return service.update(id, estadoPagoCita).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
