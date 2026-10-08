package com.therateam.therateam.controller;

import com.therateam.therateam.dto.EstadoDeCuentaDTO;
import com.therateam.therateam.dto.ReconciliacionDTO;
import com.therateam.therateam.service.EstadoDeCuentaService;
import com.therateam.therateam.service.ReconciliadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * El libro de movimientos, de solo lectura.
 *
 * GET /api/libro/reconciliar     — donde el libro y lo guardado no dicen lo mismo.
 * GET /api/libro/paciente/{id}   — su dinero, movimiento a movimiento, con lo que cubrio cada sol.
 *
 * Ninguno de los dos escribe nada: mientras el libro no sea la fuente, lo guardado manda.
 *
 * El reconciliador pide MODULO_CAJA, que es quien cuadra el dinero. El estado de cuenta pide
 * MODULO_PAGOS: es informacion del paciente y la necesita quien cobra, no solo quien cierra caja.
 */
@RestController
@RequestMapping("/api/libro")
@RequiredArgsConstructor
public class LibroController {

    private final ReconciliadorService reconciliador;
    private final EstadoDeCuentaService estadoDeCuenta;

    @PreAuthorize("hasAuthority('MODULO_CAJA')")
    @GetMapping("/reconciliar")
    public ReconciliacionDTO reconciliar() { return reconciliador.revisar(); }

    @PreAuthorize("hasAuthority('MODULO_PAGOS')")
    @GetMapping("/paciente/{id}")
    public EstadoDeCuentaDTO estadoDeCuenta(@PathVariable Long id) { return estadoDeCuenta.de(id); }
}
