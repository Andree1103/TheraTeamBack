package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService service;

    /** GET /api/usuarios?page=0&size=10&nombre=jarol&correo=x&rolId=2&activo=true&sort=createdAt,desc */
    @GetMapping
    public Page<Usuario> getAll(@PageableDefault(size = 10, sort = "nombre") Pageable pageable,
                                 @RequestParam(required = false) String nombre,
                                 @RequestParam(required = false) String correo,
                                 @RequestParam(required = false) Long rolId,
                                 @RequestParam(required = false) Boolean activo) {
        return service.findAllPaged(pageable, nombre, correo, rolId, activo);
    }

    @GetMapping("/libres")
    public List<Usuario> getLibres() {
        return service.findLibres();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_SEGURIDAD_CREAR')")
    @PostMapping
    public ResponseEntity<Usuario> create(@Valid @RequestBody Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(usuario));
    }

    @PreAuthorize("hasAuthority('MODULO_SEGURIDAD_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<Usuario> update(@PathVariable Long id, @Valid @RequestBody Usuario usuario) {
        return service.update(id, usuario)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_SEGURIDAD_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
