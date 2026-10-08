package com.therateam.therateam.controller;

import com.therateam.therateam.dto.EstadoDeCuentaDTO;
import com.therateam.therateam.dto.ReconciliacionDTO;
import com.therateam.therateam.model.Reconciliacion;
import com.therateam.therateam.service.EstadoDeCuentaService;
import com.therateam.therateam.service.ReconciliadorService;
import com.therateam.therateam.service.VigilanteDelLibroService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El libro de movimientos, de solo lectura.
 *
 * GET  /api/libro/reconciliar     — donde el libro y lo guardado no dicen lo mismo, ahora mismo.
 * GET  /api/libro/paciente/{id}   — su dinero, movimiento a movimiento, con lo que cubrio cada sol.
 * GET  /api/libro/revisiones      — las ultimas revisiones nocturnas, para ver la racha.
 * POST /api/libro/revisar         — lanza una revision ahora y la deja apuntada.
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
    private final VigilanteDelLibroService vigilante;

    @PreAuthorize("hasAuthority('MODULO_CAJA')")
    @GetMapping("/reconciliar")
    public ReconciliacionDTO reconciliar() { return reconciliador.revisar(); }

    @PreAuthorize("hasAuthority('MODULO_PAGOS')")
    @GetMapping("/paciente/{id}")
    public EstadoDeCuentaDTO estadoDeCuenta(@PathVariable Long id) { return estadoDeCuenta.de(id); }

    /**
     * La racha. Es el dato que decide cuando se puede dar el paso a la fase 3: cuantos dias
     * seguidos lleva el libro diciendo lo mismo que el sistema, con trafico real encima.
     */
    @PreAuthorize("hasAuthority('MODULO_CAJA')")
    @GetMapping("/revisiones")
    public Map<String, Object> revisiones(@RequestParam(defaultValue = "30") int cuantas) {
        List<Reconciliacion> ultimas = vigilante.ultimas(cuantas);
        Reconciliacion fallo = vigilante.ultimoFallo();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("revisiones", ultimas);
        r.put("ultimoFallo", fallo);
        // Revisiones, no dias: la nocturna es una por dia pero las lanzadas a mano cuentan
        // igual. Llamarlo dias inflaria el numero justo cuando se usa para decidir si ya se
        // puede dar el paso a la fase 3, que es cuando conviene ser conservador.
        r.put("revisionesSeguidasCuadrando", seguidasCuadrando(ultimas));
        r.put("automaticasSeguidasCuadrando", ultimas.stream()
                .filter(x -> Boolean.TRUE.equals(x.getAutomatica()))
                .takeWhile(x -> Boolean.TRUE.equals(x.getCuadra())).count());
        return r;
    }

    /** Lanza la revision sin esperar a la noche. Queda apuntada como no automatica. */
    @PreAuthorize("hasAuthority('MODULO_CAJA')")
    @PostMapping("/revisar")
    public Reconciliacion revisarAhora() { return vigilante.revisarAhora(); }

    /** Cuantas revisiones seguidas, desde la mas reciente, han cuadrado. */
    private static long seguidasCuadrando(List<Reconciliacion> ultimas) {
        return ultimas.stream().takeWhile(r -> Boolean.TRUE.equals(r.getCuadra())).count();
    }
}
