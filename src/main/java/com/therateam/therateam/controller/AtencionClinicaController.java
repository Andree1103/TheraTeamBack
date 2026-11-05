package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.dto.AtencionClinicaRequest;
import com.therateam.therateam.model.AtencionClinica;
import com.therateam.therateam.service.AtencionClinicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/atencion-clinica", "/api/atenciones"})
@RequiredArgsConstructor
public class AtencionClinicaController {

    private final AtencionClinicaService service;

    @GetMapping
    public List<AtencionClinica> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<AtencionClinica> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cita/{citaId}")
    public ResponseEntity<AtencionClinica> getByCita(@PathVariable Long citaId) {
        return service.findByCita(citaId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Las atenciones de un paciente de una sola vez. El perfil preguntaba "¿esta cita tiene
     * atención?" cita por cita: un paciente con 60 citas disparaba 60 peticiones al abrirlo,
     * y las que no tenían atención respondían 404 llenando la consola de errores falsos.
     */
    @GetMapping("/paciente/{pacienteId}")
    public List<AtencionClinica> getByPaciente(@PathVariable Long pacienteId) {
        return service.findByPaciente(pacienteId);
    }

    /**
     * Registra la atención de una cita:
     * guarda atencion_clinica + métricas, actualiza sesion y tratamiento.
     */
    @PreAuthorize("hasAuthority('MODULO_CITAS_CREAR')")
    @PostMapping
    public ResponseEntity<AtencionClinica> registrar(@RequestBody AtencionClinicaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(req));
    }

    /**
     * POST /api/atenciones/inasistencia — el paciente no vino.
     *
     * Deja la constancia en Atenciones (tipo INASISTENCIA + motivo) y pone la cita en No asistio.
     * El motivo es obligatorio: sin el, la fila no responde nada que no dijera ya el estado.
     */
    @PreAuthorize("hasAuthority('MODULO_CITAS_CREAR')")
    @PostMapping("/inasistencia")
    public ResponseEntity<AtencionClinica> registrarInasistencia(@RequestBody InasistenciaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrarInasistencia(req.getCitaId(), req.getMotivo(), req.getFecha(),
                        Boolean.TRUE.equals(req.getDevolver())));
    }

    /** Lo minimo para anotar que no vino: de que cita se trata y por que. */
    @lombok.Data
    public static class InasistenciaRequest {
        private Long citaId;
        private String motivo;
        private java.time.LocalDateTime fecha;
        /** true = el dinero cobrado vuelve al paciente como saldo a favor. */
        private Boolean devolver;
    }

    /**
     * POST /api/atenciones/no-marco — nadie marco esta cita.
     *
     * El tercer resultado. No mueve dinero ni cambia el estado de la cita: deja la constancia de
     * que ya se noto que no se sabe que paso, con quien y cuando. Ver
     * AtencionClinicaService#registrarNoMarco.
     *
     * El motivo es opcional, al contrario que en la inasistencia: aqui el dato es que nadie
     * marco, y quien repasa el mes casi nunca sabe por que.
     */
    @PreAuthorize("hasAuthority('MODULO_CITAS_CREAR')")
    @PostMapping("/no-marco")
    public ResponseEntity<AtencionClinica> registrarNoMarco(@RequestBody NoMarcoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrarNoMarco(req.getCitaId(), req.getMotivo()));
    }

    /** De que cita se trata y, si se sabe, por que se paso. */
    @lombok.Data
    public static class NoMarcoRequest {
        private Long citaId;
        private String motivo;
    }

    @PreAuthorize("hasAuthority('MODULO_CITAS_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<AtencionClinica> update(@PathVariable Long id, @RequestBody AtencionClinica atencion) {
        return service.update(id, atencion).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CITAS_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
