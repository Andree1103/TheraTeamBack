package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.dto.SesionDTO;
import com.therateam.therateam.dto.TratamientoCoberturaDTO;
import com.therateam.therateam.dto.TratamientoDTO;
import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.service.CitaService;
import com.therateam.therateam.service.TratamientoService;
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
@RequestMapping("/api/tratamientos")
@RequiredArgsConstructor
public class TratamientoController {

    private final TratamientoService service;
    private final CitaService citaService;

    /** El celular es un dato sensible: por defecto ningún usuario lo ve, salvo que se le active
     *  el permiso puntual desde Seguridad > Usuarios. */
    private TratamientoDTO redactarTelefono(TratamientoDTO dto) {
        if (dto != null && !SecurityUtils.puedeVerTelefonoPacientes()) dto.setPacienteTelefono(null);
        return dto;
    }

    /** GET /api/tratamientos?page=0&size=20&paciente=x&terapeuta=x&tipoTerapiaId=1&estado=EN_CURSO */
    @GetMapping
    public Page<TratamientoDTO> getAll(@PageableDefault(size = 20) Pageable pageable,
                                        @RequestParam(required = false) String paciente,
                                        @RequestParam(required = false) String terapeuta,
                                        @RequestParam(required = false) Long tipoTerapiaId,
                                        @RequestParam(required = false) String estado) {
        return service.findAllPaged(pageable, paciente, terapeuta, tipoTerapiaId, estado).map(this::redactarTelefono);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TratamientoDTO> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(service::toDTO)
                .map(this::redactarTelefono)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/sesiones")
    public ResponseEntity<List<SesionDTO>> getSesiones(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSesionesByTratamiento(id));
    }

    /** Cuántas sesiones ya tienen cita creada y cuántas de esas están pagadas/pendientes. */
    @GetMapping("/{id}/cobertura")
    public ResponseEntity<TratamientoCoberturaDTO> getCobertura(@PathVariable Long id) {
        return ResponseEntity.ok(service.cobertura(id));
    }

    /** GET /api/tratamientos/paciente/{id}?page=0&size=20 */
    @GetMapping("/paciente/{pacienteId}")
    public Page<TratamientoDTO> getByPaciente(@PathVariable Long pacienteId,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return service.findByPacientePaged(pacienteId, pageable).map(this::redactarTelefono);
    }

    /** GET /api/tratamientos/terapeuta/{id}?page=0&size=20 */
    @GetMapping("/terapeuta/{terapeutaId}")
    public Page<TratamientoDTO> getByTerapeuta(@PathVariable Long terapeutaId,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return service.findByTerapeutaPaged(terapeutaId, pageable).map(this::redactarTelefono);
    }

    @PreAuthorize("hasAuthority('MODULO_PAQUETES_CREAR')")
    @PostMapping
    public ResponseEntity<Tratamiento> create(@Valid @RequestBody Tratamiento t) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(t));
    }

    @PreAuthorize("hasAuthority('MODULO_PAQUETES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<Tratamiento> update(@PathVariable Long id, @Valid @RequestBody Tratamiento t) {
        return service.update(id, t).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_PAQUETES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * POST /api/tratamientos/{id}/anular?devolucion=SALDO|DINERO&metodoId=1 — anula TODAS las
     * citas pendientes del paquete (no toca las ya ASISTIDA ni las ya canceladas) y resuelve el
     * dinero de cada una igual que anular una cita suelta.
     */
    @PreAuthorize("hasAuthority('MODULO_PAQUETES_ELIMINAR') and hasAuthority('MODULO_CITAS_ELIMINAR')")
    @PostMapping("/{id}/anular")
    public ResponseEntity<List<CitaDTO>> anular(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "SALDO") String devolucion,
                                                 @RequestParam(required = false) Long metodoId) {
        return ResponseEntity.ok(citaService.anularPaquete(id, devolucion, metodoId));
    }
}
