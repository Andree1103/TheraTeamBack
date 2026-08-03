package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

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

    /** GET /api/terapeutas?page=0&size=20&nombre=x&cmp=x&areaId=1&activo=true */
    @GetMapping
    public Page<TerapeutaDTO> getAll(@PageableDefault(size = 20) Pageable pageable,
                                      @RequestParam(required = false) String nombre,
                                      @RequestParam(required = false) String cmp,
                                      @RequestParam(required = false) Long areaId,
                                      @RequestParam(required = false) Boolean activo) {
        return service.findAllPaged(pageable, nombre, cmp, areaId, activo);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Terapeuta> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_TERAPEUTAS_CREAR')")
    @PostMapping
    public ResponseEntity<Terapeuta> create(@RequestBody Terapeuta terapeuta) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(terapeuta));
    }

    @PreAuthorize("hasAuthority('MODULO_TERAPEUTAS_CREAR')")
    @PostMapping("/completo")
    public ResponseEntity<Terapeuta> createCompleto(@RequestBody TerapeutaCompletoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCompleto(req));
    }

    @PreAuthorize("hasAuthority('MODULO_TERAPEUTAS_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<Terapeuta> update(@PathVariable Long id, @RequestBody Terapeuta terapeuta) {
        return service.update(id, terapeuta)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_TERAPEUTAS_EDITAR')")
    @PutMapping("/{id}/completo")
    public ResponseEntity<Terapeuta> updateCompleto(@PathVariable Long id, @RequestBody TerapeutaCompletoRequest req) {
        return service.actualizarCompleto(id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_TERAPEUTAS_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
