package com.therateam.therateam.controller;

import com.therateam.therateam.dto.CobroCreditoDTO;
import com.therateam.therateam.dto.CobroCreditoRequest;
import com.therateam.therateam.dto.CreditoAmarreDTO;
import com.therateam.therateam.dto.CreditoLineaDTO;
import com.therateam.therateam.dto.CuentaCreditoDTO;
import com.therateam.therateam.model.CuentaCredito;
import com.therateam.therateam.service.CuentaCreditoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Las cuentas de credito: quien responde por lo que se atendio hoy y se paga despues.
 *
 * GET    /api/creditos                      — el reporte de cobranza. ?tipo=TRABAJADOR da "que
 *                                             terapeutas deben"; ?tipo=PACIENTE, los pacientes.
 * GET    /api/creditos/{id}                 — una cuenta con su deuda al dia.
 * GET    /api/creditos/{id}/lineas          — que se atendio sin cobrar, cita por cita.
 * GET    /api/creditos/{id}/pacientes       — a quien esta amarrado el credito.
 * GET    /api/creditos/paciente/{pacienteId} — a que cuenta se le puede cargar este paciente.
 * POST   /api/creditos                      — abrir una cuenta.
 * PUT    /api/creditos/{id}                 — tipo, tope, notas, activar/desactivar.
 * POST   /api/creditos/{id}/pacientes/{pid} — amarrar un paciente.
 * DELETE /api/creditos/{id}/pacientes/{pid} — desamarrarlo (lo desactiva, no lo borra).
 *
 * POST   /api/creditos/{id}/cobrar        — saldar lo del mes, de una vez.
 *
 * DOS PERMISOS DISTINTOS, porque son dos actos distintos.
 *
 * DAR credito (abrir cuentas, amarrar pacientes, cargar atenciones) pide PUEDE_DAR_CREDITO: es
 * decidir que la clinica se queda esperando el dinero.
 *
 * COBRARLO pide MODULO_PAGOS_CREAR, el mismo permiso de registrar cualquier cobro. El cajero
 * cobra; no tiene por que poder fiar. Y las LECTURAS abren a MODULO_PAGOS por lo mismo: quien
 * cobra tiene que poder ver que cobrar, o el reporte no sirve para nada.
 *
 * El service vuelve a comprobar el permiso de dar credito en cada escritura que lo concede. No es
 * redundante de gratis: esos metodos tambien se llaman desde los cuatro caminos de creacion de
 * citas y paquetes, que entran por otros controladores con otras anotaciones.
 *
 * No hay DELETE de cuenta a proposito. Una cuenta con historia no se borra —las citas y los
 * paquetes la referencian— y una cuenta vacia no molesta a nadie. Para dejar de usarla, se
 * desactiva con el PUT.
 */
@RestController
@RequestMapping("/api/creditos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PUEDE_DAR_CREDITO') or hasAuthority('MODULO_PAGOS')")
public class CuentaCreditoController {

    private final CuentaCreditoService service;

    /**
     * Cobrar la cuenta. Es el acto del cajero, no del que fia: pide MODULO_PAGOS_CREAR.
     *
     * Para descuento de sueldo se manda el metodo "Descuento de planilla", que esta marcado como
     * que no es dinero en caja — el cobro se registra, la deuda se salda y el arqueo del dia no lo
     * suma, porque no entro plata al cajon. No hay un segundo camino para el descuento: es el
     * mismo cobro con otro metodo.
     */
    @PreAuthorize("hasAuthority('MODULO_PAGOS_CREAR')")
    @PostMapping("/{id}/cobrar")
    public CobroCreditoDTO cobrar(@PathVariable Long id, @RequestBody CobroCreditoRequest req) {
        return service.cobrar(id, req);
    }

    @GetMapping
    public List<CuentaCreditoDTO> listar(@RequestParam(required = false) String tipo,
                                         @RequestParam(defaultValue = "false") boolean soloConDeuda) {
        return service.listar(tipoDe(tipo), soloConDeuda);
    }

    @GetMapping("/{id}")
    public CuentaCreditoDTO detalle(@PathVariable Long id) {
        return service.detalle(id);
    }

    /** Lo pendiente por omision: es la vista con la que se cobra a fin de mes. Con
     *  ?soloPendientes=false sale todo, incluido lo ya pagado, para responder "y esto de que era". */
    @GetMapping("/{id}/lineas")
    public List<CreditoLineaDTO> lineas(@PathVariable Long id,
                                        @RequestParam(defaultValue = "true") boolean soloPendientes) {
        return service.lineasDe(id, soloPendientes);
    }

    @GetMapping("/{id}/pacientes")
    public List<CreditoAmarreDTO> pacientes(@PathVariable Long id) {
        return service.amarresDe(id);
    }

    /** Para que la pantalla de citas pueda ofrecer "a credito" sin que nadie tenga que acordarse
     *  de a quien se le carga. 204 si el paciente no esta amarrado a ninguna cuenta. */
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<CuentaCreditoDTO> cuentaDePaciente(@PathVariable Long pacienteId) {
        return service.cuentaDePaciente(pacienteId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasAuthority('PUEDE_DAR_CREDITO')")
    @PostMapping
    public ResponseEntity<CuentaCredito> crear(@RequestBody Map<String, Object> body) {
        CuentaCredito creada = service.crear(
                numeroLargo(body.get("titularUsuarioId")),
                tipoDe(texto(body.get("tipo"))),
                importe(body.get("limite")),
                texto(body.get("notas")));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PreAuthorize("hasAuthority('PUEDE_DAR_CREDITO')")
    @PutMapping("/{id}")
    public CuentaCredito actualizar(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Boolean activo = body.get("activo") instanceof Boolean b ? b : null;
        return service.actualizar(id, tipoDe(texto(body.get("tipo"))),
                importe(body.get("limite")), activo, texto(body.get("notas")));
    }

    @PreAuthorize("hasAuthority('PUEDE_DAR_CREDITO')")
    @PostMapping("/{id}/pacientes/{pacienteId}")
    public CreditoAmarreDTO amarrar(@PathVariable Long id, @PathVariable Long pacienteId) {
        return service.amarrar(id, pacienteId);
    }

    @PreAuthorize("hasAuthority('PUEDE_DAR_CREDITO')")
    @DeleteMapping("/{id}/pacientes/{pacienteId}")
    public ResponseEntity<Void> desamarrar(@PathVariable Long id, @PathVariable Long pacienteId) {
        service.desamarrar(id, pacienteId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------------------------------

    /** Un tipo que no existe es un 400 con los que si, no un 500 ni un null silencioso. */
    private static CuentaCredito.Tipo tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) return null;
        try {
            return CuentaCredito.Tipo.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Tipo de cuenta no valido: '" + tipo + "'. Los que hay son TRABAJADOR, PACIENTE y CONVENIO.");
        }
    }

    private static String texto(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static Long numeroLargo(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identificador no valido: '" + o + "'");
        }
    }

    private static BigDecimal importe(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Importe no valido: '" + o + "'");
        }
    }
}
