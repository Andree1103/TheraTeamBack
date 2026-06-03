package com.therateam.therateam.controller;

import com.therateam.therateam.dto.TerapeutaCompletoRequest;
import com.therateam.therateam.dto.TerapeutaDTO;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.service.TerapeutaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/terapeutas")
@RequiredArgsConstructor
public class TerapeutaController {

    private final TerapeutaService service;

    /** GET /api/terapeutas?page=0&size=20 */
    @GetMapping
    public Page<TerapeutaDTO> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return service.findAllPaged(pageable);
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

    @PostMapping("/completo")
    public ResponseEntity<Terapeuta> createCompleto(@RequestBody TerapeutaCompletoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCompleto(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Terapeuta> update(@PathVariable Long id, @RequestBody Terapeuta terapeuta) {
        return service.update(id, terapeuta)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/completo")
    public ResponseEntity<Terapeuta> updateCompleto(@PathVariable Long id, @RequestBody TerapeutaCompletoRequest req) {
        return service.actualizarCompleto(id, req)
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
