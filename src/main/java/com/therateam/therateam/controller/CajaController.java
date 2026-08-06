package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.dto.CajaResumenDTO;
import com.therateam.therateam.dto.CerrarCajaRequest;
import com.therateam.therateam.model.CierreCaja;
import com.therateam.therateam.service.CajaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/caja")
@RequiredArgsConstructor
public class CajaController {

    private final CajaService service;

    /** GET /api/caja/resumen?fecha=2026-07-21&turno=1 */
    @GetMapping("/resumen")
    public CajaResumenDTO resumen(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                   @RequestParam(required = false) Integer turno) {
        return service.resumenDia(fecha, turno);
    }

    @PreAuthorize("hasAuthority('MODULO_CAJA_CREAR')")
    @PostMapping("/cerrar")
    public CajaResumenDTO cerrar(@RequestBody CerrarCajaRequest req) {
        return service.cerrarDia(req);
    }

    /** GET /api/caja/historial?desde=2026-07-01&hasta=2026-07-21 */
    @GetMapping("/historial")
    public List<CierreCaja> historial(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.historial(desde, hasta);
    }

    /** GET /api/caja/hora-corte — hora (HH:mm) que separa el turno 1 del turno 2. */
    @GetMapping("/hora-corte")
    public java.util.Map<String, String> horaCorte() {
        return java.util.Map.of("horaCorte", service.horaCorte().toString());
    }

    @PreAuthorize("hasAuthority('MODULO_CAJA_EDITAR')")
    @PutMapping("/hora-corte")
    public java.util.Map<String, String> actualizarHoraCorte(@RequestBody java.util.Map<String, String> body) {
        return java.util.Map.of("horaCorte", service.actualizarHoraCorte(body.get("horaCorte")));
    }
}
