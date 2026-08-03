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

    /** GET /api/caja/resumen?fecha=2026-07-21 */
    @GetMapping("/resumen")
    public CajaResumenDTO resumen(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.resumenDia(fecha);
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
}
