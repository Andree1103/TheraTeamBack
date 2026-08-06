package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.service.PacienteService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService service;

    /**
     * Si el usuario tiene `citasSoloPropias=true` (terapeuta restringido a sus propias citas),
     * devuelve su terapeutaId para acotar también qué pacientes ve — solo los suyos, no todos.
     */
    private Long restriccionTerapeutaId(Authentication auth) {
        if (!(auth.getDetails() instanceof Claims claims)) return null;
        if (!Boolean.TRUE.equals(claims.get("citasSoloPropias", Boolean.class))) return null;
        Number terapeutaId = claims.get("terapeutaId", Number.class);
        return terapeutaId != null ? terapeutaId.longValue() : -1L;
    }

    /** GET /api/pacientes?page=0&size=20&sort=apellido,asc&nombre=x&dni=x&correo=x&sedeId=1&activo=true */
    @GetMapping
    public Page<Paciente> getAll(@PageableDefault(size = 20, sort = "apellido") Pageable pageable,
                                  @RequestParam(required = false) String nombre,
                                  @RequestParam(required = false) String dni,
                                  @RequestParam(required = false) String correo,
                                  @RequestParam(required = false) Long sedeId,
                                  @RequestParam(required = false) Boolean activo,
                                  Authentication auth) {
        return service.findAllPaged(pageable, nombre, dni, correo, sedeId, activo, restriccionTerapeutaId(auth));
    }

    @GetMapping("/buscar")
    public ResponseEntity<Paciente> buscarPorDni(@RequestParam String dni) {
        return service.findByDni(dni)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Paciente> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_PACIENTES_CREAR')")
    @PostMapping
    public ResponseEntity<Paciente> create(@Valid @RequestBody Paciente paciente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(paciente));
    }

    @PreAuthorize("hasAuthority('MODULO_PACIENTES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<Paciente> update(@PathVariable Long id, @Valid @RequestBody Paciente paciente) {
        return service.update(id, paciente)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_PACIENTES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
