package com.therateam.therateam.service;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.dto.CobroCreditoDTO;
import com.therateam.therateam.dto.CobroCreditoRequest;
import com.therateam.therateam.dto.CreditoAmarreDTO;
import com.therateam.therateam.dto.CreditoLineaDTO;
import com.therateam.therateam.dto.CuentaCreditoDTO;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El crédito: atender o vender hoy y cobrar después, con alguien que responde.
 *
 * Resuelve el caso que la clínica tiene todas las semanas y el sistema no sabía nombrar: el
 * familiar de un terapeuta que se atiende hoy y se paga a fin de mes. Sin una salida legítima, el
 * mostrador usaba dos atajos —cobrar un efectivo que no entró, o dejar la cita en Programada para
 * siempre— y en los dos casos nadie sabía cuánto había por cobrar ni a quién.
 *
 * TRES REGLAS, y las tres están aquí porque la base no puede comprobarlas sola:
 *
 * 1. Hace falta el permiso PUEDE_DAR_CREDITO. Dar crédito no es agendar ni cobrar: es decidir que
 *    la clínica se queda esperando el dinero.
 * 2. Nadie se da crédito a sí mismo, lo tenga o no. Es la deuda de su familia, y quien se
 *    beneficia no puede ser quien la autoriza.
 * 3. El límite avisa, no bloquea. Pasarse no impide cargar la cita; devuelve un aviso. Bloquear
 *    en el mostrador, con el paciente delante, devuelve el atajo del efectivo fantasma.
 *
 * Lo que sí garantiza la base: que la cuenta cubra a ese paciente (FK compuesta), que un paciente
 * esté activo en una sola cuenta, y que una cita de paquete no se cargue aquí — su deuda la lleva
 * el tratamiento, y contarla por las dos puntas sumaría el mismo paquete dos veces.
 */
@Service
@RequiredArgsConstructor
public class CuentaCreditoService {

    private final CuentaCreditoRepository repository;
    private final CuentaCreditoPacienteRepository amarreRepository;
    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final TerapeutaRepository terapeutaRepository;
    private final CitaRepository citaRepository;
    private final TratamientoRepository tratamientoRepository;
    private final CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;
    private final CatMetodoPagoRepository catMetodoPagoRepository;
    private final PagoService pagoService;

    private static final List<String> ESTADOS_CITA_QUE_NO_SE_COBRAN =
            List.of("ANULADA", "REPROGRAMADA", "CANCELADA_PACIENTE", "CANCELADA_CLINICA");

    // =============================================================================================
    // Aplicar crédito al crear — los cuatro caminos
    // =============================================================================================

    /**
     * Carga una cita suelta a una cuenta de crédito, antes de guardarla.
     *
     * La usan los tres caminos de creación de citas (la normal, la rápida y la masiva) para que la
     * regla viva en un solo sitio: si cada uno la comprobara por su cuenta, el día que se agregue
     * un cuarto camino se olvidaría, y lo que se olvida aquí es quién paga.
     *
     * Deja la cita en A_CREDITO y devuelve un aviso si la cuenta se pasó del límite, o null.
     * No guarda: el que llama hace el save, dentro de su propia transacción.
     */
    @Transactional
    public String aplicarACita(Cita cita, Long cuentaCreditoId) {
        if (cuentaCreditoId == null) return null;

        // Una cita de paquete no se carga aquí. La base también lo impide
        // (ck_citas_credito_solo_sueltas), pero un 400 explicando por qué es más útil que un
        // choque de constraint: lo que debe es el paquete, y el crédito va en el tratamiento.
        if (cita.getSesion() != null) {
            throw new IllegalArgumentException(
                    "Esta cita es de un paquete: el crédito se le da al paquete, no a la sesión. "
                  + "Carga el paquete a la cuenta al crearlo.");
        }

        CuentaCredito cuenta = exigirCuentaUsable(cuentaCreditoId);
        Paciente paciente = cita.getPaciente();
        if (paciente == null || paciente.getId() == null) {
            throw new IllegalArgumentException("No se puede dar crédito a una cita sin paciente.");
        }
        asegurarAmarre(cuenta, paciente);

        cita.setCuentaCredito(cuenta);
        catEstadoPagoCitaRepository.findByKey("A_CREDITO").ifPresent(cita::setEstadoPago);

        return avisoDeLimite(cuenta, deudaDe(cuenta.getId()));
    }

    /**
     * Carga un paquete a una cuenta de crédito, antes de guardarlo.
     *
     * El crédito de un paquete va en el tratamiento porque es donde está su dinero: su deuda es
     * montoTotal - totalCobrado, y es contra el tratamiento que se cobra.
     */
    @Transactional
    public String aplicarAPaquete(Tratamiento tratamiento, Long cuentaCreditoId) {
        if (cuentaCreditoId == null) return null;

        CuentaCredito cuenta = exigirCuentaUsable(cuentaCreditoId);
        Paciente paciente = tratamiento.getPaciente();
        if (paciente == null || paciente.getId() == null) {
            throw new IllegalArgumentException("No se puede dar crédito a un paquete sin paciente.");
        }
        asegurarAmarre(cuenta, paciente);

        tratamiento.setCuentaCredito(cuenta);

        return avisoDeLimite(cuenta, deudaDe(cuenta.getId()));
    }

    /**
     * Las tres comprobaciones que la base no puede hacer, en el orden en que importan.
     *
     * El permiso primero: a quien no puede dar crédito no se le cuenta si la cuenta existe.
     */
    private CuentaCredito exigirCuentaUsable(Long cuentaCreditoId) {
        if (!SecurityUtils.puedeDarCredito()) {
            throw new AccessDeniedException(
                    "No tienes permiso para dar crédito. Se activa por rol en Seguridad > Roles.");
        }

        CuentaCredito cuenta = repository.findById(cuentaCreditoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cuenta de crédito no encontrada: " + cuentaCreditoId));

        if (!cuenta.estaActiva()) {
            throw new IllegalArgumentException(
                    "La cuenta de crédito de " + nombreDe(cuenta.getTitular()) + " está desactivada.");
        }

        // Nadie se da crédito a sí mismo. Tener el permiso no alcanza: un terapeuta que además
        // puede crear citas se auto-aprobaría las de su madre.
        Long yo = SecurityUtils.currentUserId();
        if (yo != null && cuenta.getTitular() != null && yo.equals(cuenta.getTitular().getId())) {
            throw new AccessDeniedException(
                    "No puedes cargar atenciones a tu propia cuenta de crédito. "
                  + "Tiene que autorizarlo otra persona.");
        }

        return cuenta;
    }

    /**
     * Amarra el paciente a la cuenta si no lo estaba, o reactiva el amarre si estaba desactivado.
     *
     * Se hace solo, en el mismo acto de dar el crédito, porque dar crédito a alguien ES amarrarlo:
     * mandar a la recepcionista a otra pantalla primero, con el paciente esperando en el mostrador,
     * es exactamente la friccion que hace que se vuelva al atajo del efectivo fantasma.
     *
     * Si el paciente ya está activo en OTRA cuenta, no se mueve solo: eso es cambiarle de
     * responsable, y es una decisión, no un trámite. La base lo impediría igual (un paciente
     * activo en una sola cuenta), pero el mensaje explica a quién está amarrado hoy.
     */
    private void asegurarAmarre(CuentaCredito cuenta, Paciente paciente) {
        Optional<CuentaCreditoPaciente> enEstaCuenta =
                amarreRepository.findByCuentaIdAndPacienteId(cuenta.getId(), paciente.getId());

        if (enEstaCuenta.isPresent()) {
            CuentaCreditoPaciente amarre = enEstaCuenta.get();
            if (!amarre.estaActivo()) {
                amarre.setActivo(true);
                amarreRepository.save(amarre);
            }
            return;
        }

        Optional<CuentaCreditoPaciente> enOtraCuenta = amarreRepository.amarreVigenteDe(paciente.getId());
        if (enOtraCuenta.isPresent()) {
            CuentaCredito otra = enOtraCuenta.get().getCuenta();
            throw new IllegalArgumentException(
                    nombreDe(paciente) + " ya está amarrado a la cuenta de " + nombreDe(otra.getTitular())
                  + ". Desamárralo de esa cuenta antes de cargarlo a esta.");
        }

        CuentaCreditoPaciente amarre = new CuentaCreditoPaciente();
        amarre.setCuenta(cuenta);
        amarre.setPaciente(paciente);
        amarre.setActivo(true);
        amarreRepository.save(amarre);
    }

    /** El aviso de que la cuenta se pasó del tope, o null. Avisa: nunca bloquea. */
    private String avisoDeLimite(CuentaCredito cuenta, BigDecimal deuda) {
        if (!cuenta.sePasaDelLimite(deuda)) return null;
        return "Aviso: la cuenta de " + nombreDe(cuenta.getTitular()) + " acumula S/ " + deuda
             + " y su tope es S/ " + cuenta.getLimite() + ".";
    }

    // =============================================================================================
    // Las cuentas
    // =============================================================================================

    @Transactional
    public CuentaCredito crear(Long titularUsuarioId, CuentaCredito.Tipo tipo,
                               BigDecimal limite, String notas) {
        exigirPermiso();

        if (titularUsuarioId == null) throw new IllegalArgumentException("Falta el titular de la cuenta.");
        if (tipo == null) throw new IllegalArgumentException("Falta el tipo de cuenta.");
        if (limite != null && limite.signum() < 0) {
            throw new IllegalArgumentException("El tope no puede ser negativo.");
        }

        Usuario titular = usuarioRepository.findById(titularUsuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + titularUsuarioId));

        // Una cuenta por persona. El mensaje dice que ya existe en vez de dejar que choque la
        // constraint, porque lo que quiere quien lo intenta es abrir la que ya hay.
        if (repository.existsByTitularId(titularUsuarioId)) {
            throw new IllegalArgumentException(
                    nombreDe(titular) + " ya tiene una cuenta de crédito. Ábrela para cargarle atenciones.");
        }

        CuentaCredito cuenta = new CuentaCredito();
        cuenta.setTitular(titular);
        cuenta.setTipo(tipo);
        cuenta.setLimite(limite);
        cuenta.setNotas(notas);
        cuenta.setActivo(true);
        return repository.save(cuenta);
    }

    @Transactional
    public CuentaCredito actualizar(Long id, CuentaCredito.Tipo tipo, BigDecimal limite,
                                    Boolean activo, String notas) {
        exigirPermiso();

        CuentaCredito cuenta = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de crédito no encontrada: " + id));

        if (limite != null && limite.signum() < 0) {
            throw new IllegalArgumentException("El tope no puede ser negativo.");
        }
        if (tipo != null) cuenta.setTipo(tipo);
        cuenta.setLimite(limite);
        if (notas != null) cuenta.setNotas(notas);

        // Desactivar no borra ni perdona: corta las cargas nuevas y la deuda sigue ahí, por eso
        // se puede hacer con saldo pendiente. Lo que no se puede es borrar la cuenta.
        if (activo != null) cuenta.setActivo(activo);

        return repository.save(cuenta);
    }

    /**
     * El titular no se cambia. Si la cuenta es de Rocío y resulta que debía ser de otra persona,
     * lo que hay es una cuenta mal abierta, no un titular que cambia: mover la titularidad movería
     * también las deudas ya acumuladas, que son de quien las pidió.
     */

    @Transactional(readOnly = true)
    public List<CuentaCreditoDTO> listar(CuentaCredito.Tipo tipo, boolean soloConDeuda) {
        List<CuentaCredito> cuentas = tipo != null
                ? repository.findByTipoOrderByIdAsc(tipo)
                : repository.findAll();

        // Las dos sumas en dos consultas, no dos por cuenta.
        Map<Long, Object[]> porCitas = indexar(repository.deudaEnCitasDeTodas());
        Map<Long, Object[]> porPaquetes = indexar(repository.deudaEnPaquetesDeTodas());

        List<CuentaCreditoDTO> salida = new ArrayList<>();
        for (CuentaCredito c : cuentas) {
            CuentaCreditoDTO dto = aDTO(c, porCitas.get(c.getId()), porPaquetes.get(c.getId()));
            if (soloConDeuda && dto.getDeuda().signum() <= 0) continue;
            salida.add(dto);
        }
        // De mayor deuda a menor: el reporte existe para cobrar, y se empieza por el que más debe.
        salida.sort((a, b) -> b.getDeuda().compareTo(a.getDeuda()));
        return salida;
    }

    @Transactional(readOnly = true)
    public CuentaCreditoDTO detalle(Long id) {
        CuentaCredito c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de crédito no encontrada: " + id));
        Map<Long, Object[]> porCitas = indexar(repository.deudaEnCitasDeTodas());
        Map<Long, Object[]> porPaquetes = indexar(repository.deudaEnPaquetesDeTodas());
        return aDTO(c, porCitas.get(id), porPaquetes.get(id));
    }

    /** La cuenta a la que está amarrado un paciente, si a alguna. La pantalla de citas la consulta
     *  para poder ofrecer "a crédito" sin que nadie tenga que acordarse de a quién se le carga. */
    @Transactional(readOnly = true)
    public Optional<CuentaCreditoDTO> cuentaDePaciente(Long pacienteId) {
        return amarreRepository.amarreVigenteDe(pacienteId)
                .map(a -> detalle(a.getCuenta().getId()));
    }

    // =============================================================================================
    // Los amarres — "a quién está amarrado el crédito"
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<CreditoAmarreDTO> amarresDe(Long cuentaId) {
        return amarreRepository.findByCuentaIdOrderByActivoDescIdAsc(cuentaId).stream()
                .map(a -> new CreditoAmarreDTO(
                        a.getId(),
                        a.getPaciente() != null ? a.getPaciente().getId() : null,
                        nombreDe(a.getPaciente()),
                        a.getPaciente() != null ? a.getPaciente().getDni() : null,
                        a.estaActivo(),
                        a.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CreditoAmarreDTO amarrar(Long cuentaId, Long pacienteId) {
        exigirPermiso();
        CuentaCredito cuenta = repository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de crédito no encontrada: " + cuentaId));
        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado: " + pacienteId));
        asegurarAmarre(cuenta, paciente);
        return amarresDe(cuentaId).stream()
                .filter(a -> pacienteId.equals(a.getPacienteId()))
                .findFirst().orElseThrow();
    }

    /**
     * Desamarrar DESACTIVA, no borra.
     *
     * Las citas ya cargadas apuntan a este par con una FK compuesta, así que borrar la fila
     * fallaría mientras exista historia — y si se pudiera borrar, se perdería el dato de quién
     * respondía cuando se atendió. Desamarrar significa "no le cargues más", no "esto nunca pasó".
     *
     * Se permite con deuda pendiente a propósito: dejar de fiarle a alguien es justo lo que se
     * quiere hacer cuando ya debe de más.
     */
    @Transactional
    public void desamarrar(Long cuentaId, Long pacienteId) {
        exigirPermiso();
        CuentaCreditoPaciente amarre = amarreRepository
                .findByCuentaIdAndPacienteId(cuentaId, pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Ese paciente no está amarrado a esta cuenta."));
        amarre.setActivo(false);
        amarreRepository.save(amarre);
    }

    // =============================================================================================
    // El detalle: qué se atendió sin cobrar
    // =============================================================================================

    /**
     * Las líneas de una cuenta: sus citas sueltas y sus paquetes, lo cobrado y lo que falta.
     *
     * Con {@code soloPendientes} se queda con lo que de verdad hay que cobrar, que es la vista con
     * la que se cobra a fin de mes. Sin el filtro sale todo, incluido lo ya pagado, que es la
     * vista con la que se responde "¿y esto de qué era?".
     */
    @Transactional(readOnly = true)
    public List<CreditoLineaDTO> lineasDe(Long cuentaId, boolean soloPendientes) {
        List<CreditoLineaDTO> lineas = new ArrayList<>();

        for (Cita c : citaRepository.findByCuentaCreditoIdAndEliminadoFalse(cuentaId)) {
            String estadoKey = c.getEstado() != null ? c.getEstado().getKey() : null;
            if (ESTADOS_CITA_QUE_NO_SE_COBRAN.contains(estadoKey)) continue;

            BigDecimal monto = c.getPrecio() != null ? c.getPrecio() : BigDecimal.ZERO;
            BigDecimal pagado = c.getMontoPagado() != null ? c.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal deuda = monto.subtract(pagado);
            if (soloPendientes && deuda.signum() <= 0) continue;

            lineas.add(new CreditoLineaDTO(
                    "CITA", c.getId(), c.getFechaInicio(),
                    c.getPaciente() != null ? c.getPaciente().getId() : null,
                    nombreDe(c.getPaciente()),
                    nombreTerapeuta(c.getTerapeuta()),
                    c.getTipoTerapia() != null ? c.getTipoTerapia().getNombre() : "Cita",
                    c.getEstado() != null ? c.getEstado().getNombre() : null,
                    c.getEstadoPago() != null ? c.getEstadoPago().getNombre() : null,
                    monto, pagado, deuda));
        }

        for (Tratamiento t : tratamientoRepository.findByCuentaCreditoId(cuentaId)) {
            String estadoKey = t.getEstado() != null ? t.getEstado().getKey() : null;
            if ("CANCELADO".equals(estadoKey)) continue;

            BigDecimal monto = t.getMontoTotal() != null ? t.getMontoTotal() : BigDecimal.ZERO;
            BigDecimal pagado = t.getTotalCobrado() != null ? t.getTotalCobrado() : BigDecimal.ZERO;
            BigDecimal deuda = monto.subtract(pagado);
            if (soloPendientes && deuda.signum() <= 0) continue;

            lineas.add(new CreditoLineaDTO(
                    "PAQUETE", t.getId(),
                    t.getFechaInicio() != null ? t.getFechaInicio().atStartOfDay() : null,
                    t.getPaciente() != null ? t.getPaciente().getId() : null,
                    nombreDe(t.getPaciente()),
                    nombreTerapeuta(t.getTerapeuta()),
                    t.getNombre() != null ? t.getNombre() : "Paquete",
                    t.getEstado() != null ? t.getEstado().getNombre() : null,
                    null,
                    monto, pagado, deuda));
        }

        // Lo más viejo primero: es el orden en que se cobra y el que deja ver qué se está añejando.
        lineas.sort((a, b) -> {
            if (a.getFecha() == null) return b.getFecha() == null ? 0 : 1;
            if (b.getFecha() == null) return -1;
            return a.getFecha().compareTo(b.getFecha());
        });
        return lineas;
    }

    // =============================================================================================
    // Cobrar la cuenta
    // =============================================================================================

    /**
     * Cobra lo pendiente de una cuenta, de una vez.
     *
     * NO ESCRIBE UN SEGUNDO MOTOR DE DINERO. Crea un {@link Pago} por línea y lo pasa por
     * {@link PagoService#save}, que es el único sitio donde esta aplicación sabe mover plata. De
     * ahí sale gratis y correcto todo lo que importa: el asiento del libro, el arqueo del cierre
     * (que no suma el descuento de planilla porque su método no es dinero en caja), el estado de
     * pago de la cita, el total_cobrado del paquete y el reparto entre sus sesiones.
     *
     * Un pago por línea y no uno por el total: cada cita tiene que quedar con su montoPagado al
     * día, y repartir un importe entre N citas es exactamente el motor que no hay que reescribir.
     * Los N pagos comparten referencia, así que en Pagos se ven como el cobro que fueron.
     *
     * EL SALDO A FAVOR DEL PACIENTE NO SE TOCA. Va explícito en saldoAAplicar = 0, porque
     * PagoService, cuando no se le dice nada, mete el saldo entero del paciente en el reparto. Y
     * aquí el que paga es el TITULAR, no el paciente: comerse el crédito que la madre de Rocío
     * tenía a favor para saldar una deuda de Rocío sería cobrarle dos veces a la familia.
     *
     * DE LO MÁS VIEJO A LO MÁS NUEVO. Si el titular paga menos de lo que debe, se salda lo que
     * lleva más tiempo esperando. Es el orden en que se cobra, y el que evita que una cita de hace
     * tres meses se quede al final de la cola para siempre.
     */
    @Transactional
    public CobroCreditoDTO cobrar(Long cuentaId, CobroCreditoRequest req) {
        CuentaCredito cuenta = repository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de crédito no encontrada: " + cuentaId));

        if (req == null || req.getMetodoId() == null) {
            throw new IllegalArgumentException(
                    "Falta el método de pago. Para descuento de sueldo, elige 'Descuento de planilla'.");
        }
        CatMetodoPago metodo = catMetodoPagoRepository.findById(req.getMetodoId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Método de pago no encontrado: " + req.getMetodoId()));

        // Una cuenta DESACTIVADA sí se puede cobrar. Desactivar corta las cargas nuevas, no la
        // cobranza: justo a quien se le dejó de fiar es a quien hay que cobrarle.
        List<CreditoLineaDTO> pendientes = lineasDe(cuentaId, true);

        if (req.getLineas() != null && !req.getLineas().isEmpty()) {
            Set<String> elegidas = req.getLineas().stream()
                    .filter(l -> l.getTipo() != null && l.getId() != null)
                    .map(l -> l.getTipo().trim().toUpperCase() + "#" + l.getId())
                    .collect(Collectors.toSet());
            pendientes = pendientes.stream()
                    .filter(l -> elegidas.contains(l.getTipo() + "#" + l.getId()))
                    .toList();
            if (pendientes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Ninguna de las líneas elegidas está pendiente de cobro en esta cuenta.");
            }
        }

        if (pendientes.isEmpty()) {
            throw new IllegalArgumentException("La cuenta de " + nombreDe(cuenta.getTitular())
                    + " no tiene nada pendiente de cobro.");
        }

        BigDecimal total = pendientes.stream()
                .map(CreditoLineaDTO::getDeuda)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal restante;
        if (req.getMonto() == null) {
            restante = total;
        } else {
            if (req.getMonto().signum() <= 0) {
                throw new IllegalArgumentException("El monto a cobrar tiene que ser mayor que cero.");
            }
            // Cobrar más de lo que se debe no genera saldo a favor por esta vía: el crédito es una
            // deuda, no una cuenta corriente. Si el titular quiere dejar dinero adelantado, eso es
            // un adelanto del paciente y se registra en Pagos como siempre.
            if (req.getMonto().compareTo(total) > 0) {
                throw new IllegalArgumentException("Se intenta cobrar S/ " + req.getMonto()
                        + " pero la deuda es S/ " + total + ".");
            }
            restante = req.getMonto();
        }

        String referencia = req.getReferencia() != null && !req.getReferencia().isBlank()
                ? req.getReferencia().trim()
                : "CRED-" + cuentaId + "-" + LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        List<Long> pagoIds = new ArrayList<>();
        List<Long> citasTocadas = new ArrayList<>();
        List<Long> paquetesTocados = new ArrayList<>();
        BigDecimal cobrado = BigDecimal.ZERO;
        int saldadas = 0;

        for (CreditoLineaDTO linea : pendientes) {
            if (restante.signum() <= 0) break;
            BigDecimal aAplicar = linea.getDeuda().min(restante);
            if (aAplicar.signum() <= 0) continue;

            Pago pago = new Pago();
            pago.setMetodo(metodo);
            pago.setMontoRecibido(aAplicar);
            // Lo que se pretende cubrir, para que PagoService no tenga que adivinarlo.
            pago.setMontoAplicado(aAplicar);
            // El saldo del paciente no entra en este cobro. Ver el javadoc.
            pago.setSaldoAAplicar(BigDecimal.ZERO);
            pago.setReferencia(referencia);
            pago.setNotas(notaDelCobro(cuenta, req.getNotas()));

            if ("PAQUETE".equals(linea.getTipo())) {
                Tratamiento t = tratamientoRepository.findById(linea.getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Paquete no encontrado: " + linea.getId()));
                pago.setTratamiento(t);
                pago.setPaciente(t.getPaciente());
                paquetesTocados.add(t.getId());
            } else {
                Cita c = citaRepository.findById(linea.getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Cita no encontrada: " + linea.getId()));
                pago.setCita(c);
                pago.setPaciente(c.getPaciente());
                citasTocadas.add(c.getId());
            }

            Pago guardado = pagoService.save(pago);
            pagoIds.add(guardado.getId());

            cobrado = cobrado.add(aAplicar);
            restante = restante.subtract(aAplicar);
            if (aAplicar.compareTo(linea.getDeuda()) >= 0) saldadas++;
        }

        // El detalle se vuelve a leer de la base, no se calcula: así dice lo que de verdad quedó
        // después de que PagoService hiciera su trabajo, y no lo que aquí se cree que hizo.
        List<CreditoLineaDTO> detalle = lineasDe(cuentaId, false).stream()
                .filter(l -> ("CITA".equals(l.getTipo()) && citasTocadas.contains(l.getId()))
                          || ("PAQUETE".equals(l.getTipo()) && paquetesTocados.contains(l.getId())))
                .toList();

        return new CobroCreditoDTO(
                cuentaId, nombreDe(cuenta.getTitular()),
                metodo.getNombre(), metodo.cuentaEnCajaOEsDinero(),
                cobrado, deudaDe(cuentaId),
                saldadas, citasTocadas.size() + paquetesTocados.size(),
                referencia, pagoIds, detalle);
    }

    /** La nota que queda en cada pago: de quién se cobró, para que en Pagos se entienda por qué
     *  una cita de la madre de Rocío la pagó Rocío. */
    private String notaDelCobro(CuentaCredito cuenta, String notas) {
        String base = "Cobro de crédito — " + nombreDe(cuenta.getTitular());
        return notas != null && !notas.isBlank() ? base + " — " + notas.trim() : base;
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    /** Cuánto debe la cuenta, por las dos puntas. Nunca null. */
    @Transactional(readOnly = true)
    public BigDecimal deudaDe(Long cuentaId) {
        BigDecimal citas = repository.deudaEnCitasDe(cuentaId);
        BigDecimal paquetes = repository.deudaEnPaquetesDe(cuentaId);
        return (citas != null ? citas : BigDecimal.ZERO).add(paquetes != null ? paquetes : BigDecimal.ZERO);
    }

    private void exigirPermiso() {
        if (!SecurityUtils.puedeDarCredito()) {
            throw new AccessDeniedException(
                    "No tienes permiso para administrar cuentas de crédito. "
                  + "Se activa por rol en Seguridad > Roles.");
        }
    }

    private static Map<Long, Object[]> indexar(List<Object[]> filas) {
        Map<Long, Object[]> m = new HashMap<>();
        for (Object[] f : filas) {
            if (f[0] != null) m.put(((Number) f[0]).longValue(), f);
        }
        return m;
    }

    private CuentaCreditoDTO aDTO(CuentaCredito c, Object[] deCitas, Object[] dePaquetes) {
        BigDecimal deudaCitas = importe(deCitas, 2);
        BigDecimal deudaPaquetes = importe(dePaquetes, 2);
        BigDecimal deuda = deudaCitas.add(deudaPaquetes);

        Usuario titular = c.getTitular();
        Long terapeutaId = titular != null
                ? terapeutaRepository.findByUsuarioId(titular.getId()).map(Terapeuta::getId).orElse(null)
                : null;

        return new CuentaCreditoDTO(
                c.getId(),
                titular != null ? titular.getId() : null,
                nombreDe(titular),
                titular != null ? titular.getEmail() : null,
                titular != null && titular.getRol() != null ? titular.getRol().getKey() : null,
                terapeutaId,
                c.getTipo() != null ? c.getTipo().name() : null,
                c.getLimite(),
                c.estaActiva(),
                c.getNotas(),
                deuda, deudaCitas, deudaPaquetes,
                cuantas(deCitas), cuantas(dePaquetes),
                amarreRepository.findByCuentaIdAndActivoTrueOrderByIdAsc(c.getId()).size(),
                masAntigua(fecha(deCitas), fecha(dePaquetes)),
                c.sePasaDelLimite(deuda));
    }

    private static BigDecimal importe(Object[] fila, int i) {
        if (fila == null || fila[i] == null) return BigDecimal.ZERO;
        return (BigDecimal) fila[i];
    }

    private static long cuantas(Object[] fila) {
        return fila == null || fila[1] == null ? 0L : ((Number) fila[1]).longValue();
    }

    private static LocalDateTime fecha(Object[] fila) {
        if (fila == null || fila[3] == null) return null;
        if (fila[3] instanceof LocalDateTime dt) return dt;
        if (fila[3] instanceof java.time.LocalDate d) return d.atStartOfDay();
        return null;
    }

    private static LocalDateTime masAntigua(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static String nombreDe(Usuario u) {
        if (u == null) return null;
        return (nuloAVacio(u.getNombre()) + " " + nuloAVacio(u.getApellido())).trim();
    }

    private static String nombreDe(Paciente p) {
        if (p == null) return null;
        return (nuloAVacio(p.getNombre()) + " " + nuloAVacio(p.getApellido())).trim();
    }

    private static String nombreTerapeuta(Terapeuta t) {
        return t == null ? null : nombreDe(t.getUsuario());
    }

    private static String nuloAVacio(String s) { return s == null ? "" : s; }
}
