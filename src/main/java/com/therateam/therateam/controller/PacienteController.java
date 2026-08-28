package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.UsuarioRepository;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService service;
    private final UsuarioRepository usuarioRepository;
    private final com.therateam.therateam.service.SaldoMovimientoService saldoMovimientoService;

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

    /** El celular es un dato sensible: por defecto ningún usuario lo ve, salvo que se le active
     *  el permiso puntual desde Seguridad > Usuarios. */
    private Paciente redactarTelefono(Paciente p) {
        if (p != null && !SecurityUtils.puedeVerTelefonoPacientes()) p.setTelefono(null);
        return p;
    }

    /** Resuelve `usuarioCreacionId` → "Nombre Apellido" para toda una página de una sola pasada
     *  (evita N+1: una consulta por cada id distinto, no una por fila). */
    private Page<Paciente> enriquecerCreador(Page<Paciente> page) {
        List<Long> ids = page.getContent().stream()
                .map(Paciente::getUsuarioCreacionId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return page;
        Map<Long, String> nombres = usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u.getNombre() + " " + u.getApellido()));
        page.getContent().forEach(p -> {
            if (p.getUsuarioCreacionId() != null) p.setUsuarioCreacionNombre(nombres.get(p.getUsuarioCreacionId()));
        });
        return page;
    }

    private Paciente enriquecerCreador(Paciente p) {
        if (p != null && p.getUsuarioCreacionId() != null) {
            usuarioRepository.findById(p.getUsuarioCreacionId())
                    .ifPresent(u -> p.setUsuarioCreacionNombre(u.getNombre() + " " + u.getApellido()));
        }
        return p;
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
        Page<Paciente> page = service.findAllPaged(pageable, nombre, dni, correo, sedeId, activo, restriccionTerapeutaId(auth))
                .map(this::redactarTelefono);
        return enriquecerCreador(page);
    }

    /** GET /api/pacientes/adelantos?page=0&size=20&nombre=x — pacientes con saldo a favor. */
    @GetMapping("/adelantos")
    public Page<Paciente> getAdelantos(@PageableDefault(size = 20) Pageable pageable,
                                        @RequestParam(required = false) String nombre) {
        Page<Paciente> page = service.findConSaldoAFavorPaged(pageable, nombre).map(this::redactarTelefono);
        return enriquecerUltimoMovimiento(enriquecerCreador(page));
    }

    /** GET /api/pacientes/{id}/saldo-movimientos — historial completo del saldo a favor. */
    @GetMapping("/{id}/saldo-movimientos")
    public List<com.therateam.therateam.dto.SaldoMovimientoDTO> saldoMovimientos(@PathVariable Long id) {
        return saldoMovimientoService.historial(id);
    }

    /**
     * Rellena, para cada paciente de la pagina, el motivo y el terapeuta de su ultimo movimiento
     * de saldo. Una sola consulta para toda la pagina, no una por paciente.
     */
    private Page<Paciente> enriquecerUltimoMovimiento(Page<Paciente> page) {
        List<Long> ids = page.getContent().stream().map(Paciente::getId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return page;
        Map<Long, com.therateam.therateam.dto.SaldoMovimientoDTO> ultimos = new java.util.HashMap<>();
        // La consulta viene ordenada de mas reciente a mas antiguo: el primero de cada paciente gana.
        for (var m : saldoMovimientoService.ultimosDe(ids)) {
            ultimos.putIfAbsent(m.getPacienteId(), m);
        }
        page.getContent().forEach(p -> {
            var m = ultimos.get(p.getId());
            if (m == null) return;
            p.setSaldoUltimoMotivo(m.getMotivo());
            p.setSaldoUltimaFecha(m.getFecha());
            p.setSaldoUltimoTerapeuta(m.getTerapeutaNombre());
        });
        return page;
    }

    @GetMapping("/buscar")
    public ResponseEntity<Paciente> buscarPorDni(@RequestParam String dni) {
        return service.findByDni(dni)
                .map(this::redactarTelefono)
                .map(this::enriquecerCreador)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Paciente> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(this::redactarTelefono)
                .map(this::enriquecerCreador)
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
