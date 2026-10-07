package com.therateam.therateam.controller;

import com.therateam.therateam.dto.CuadreDTO;
import com.therateam.therateam.service.CuadreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * GET  /api/cuadre          — dice donde el dinero guardado no coincide con sus pagos.
 * POST /api/cuadre/corregir — ademas lo corrige.
 *
 * Revisar no toca nada y lo puede ver cualquiera con acceso al modulo Caja — no hay autoridad
 * "_VER": el propio MODULO_CAJA ya significa que entra ahi. Corregir reescribe cifras de dinero,
 * asi que se exige el permiso de eliminar en Pagos: quien puede deshacer un cobro es quien puede
 * rehacer uno.
 */
@RestController
@RequestMapping("/api/cuadre")
@RequiredArgsConstructor
public class CuadreController {

    private final CuadreService service;

    @PreAuthorize("hasAuthority('MODULO_CAJA')")
    @GetMapping
    public CuadreDTO revisar() { return service.revisar(); }

    @PreAuthorize("hasAuthority('MODULO_PAGOS_ELIMINAR')")
    @PostMapping("/corregir")
    public CuadreDTO corregir() { return service.corregir(); }
}
