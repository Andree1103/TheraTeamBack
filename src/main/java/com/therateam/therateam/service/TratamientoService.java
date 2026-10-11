package com.therateam.therateam.service;

import com.therateam.therateam.dto.SesionDTO;
import com.therateam.therateam.dto.TratamientoCoberturaDTO;
import com.therateam.therateam.dto.TratamientoDTO;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.CatEstadoSesionRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PagoRepository;
import com.therateam.therateam.repository.SesionRepository;
import com.therateam.therateam.repository.TratamientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TratamientoService {

    private final TratamientoRepository repository;
    private final SesionRepository sesionRepository;
    private final PagoRepository pagoRepository;
    private final CitaRepository citaRepository;
    private final CatEstadoSesionRepository catEstadoSesionRepository;

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findAllPaged(Pageable pageable, String paciente, String terapeuta,
                                              Long tipoTerapiaId, String estado) {
        return conConteoDeCitas(repository.findAllProjected(blankToNull(paciente), blankToNull(terapeuta),
                tipoTerapiaId, blankToNull(estado), pageable));
    }

    /**
     * Rellena cuantas citas tiene cada paquete, cuantas atendidas y cuantas no asistidas.
     *
     * Va aqui y no en la proyeccion porque la consulta de paquetes ya trae lo suyo; sumarle tres
     * subconsultas correlacionadas la volveria lenta para todas las pantallas, cuando este dato
     * solo lo mira el listado. Una sola consulta agrupada para toda la pagina: nada de N+1.
     */
    private Page<TratamientoDTO> conConteoDeCitas(Page<TratamientoDTO> pagina) {
        List<Long> ids = pagina.getContent().stream().map(TratamientoDTO::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return pagina;
        java.util.Map<Long, long[]> porPaquete = new java.util.HashMap<>();
        for (Object[] f : citaRepository.contarCitasPorTratamiento(ids)) {
            porPaquete.put(((Number) f[0]).longValue(), new long[]{
                    f[1] == null ? 0 : ((Number) f[1]).longValue(),
                    f[2] == null ? 0 : ((Number) f[2]).longValue(),
                    f[3] == null ? 0 : ((Number) f[3]).longValue()});
        }
        pagina.getContent().forEach(dto -> {
            long[] c = porPaquete.getOrDefault(dto.getId(), new long[]{0, 0, 0});
            dto.setCitasTotal((int) c[0]);
            dto.setCitasAtendidas((int) c[1]);
            dto.setCitasNoAsistidas((int) c[2]);
        });
        return pagina;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findByPacientePaged(Long pacienteId, Pageable pageable) {
        return repository.findByPacienteIdProjected(pacienteId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TratamientoDTO> findByTerapeutaPaged(Long terapeutaId, Pageable pageable) {
        return repository.findByTerapeutaIdProjected(terapeutaId, pageable);
    }

    public List<Tratamiento> findAll() { return repository.findAll(); }
    public Optional<Tratamiento> findById(Long id) { return repository.findById(id); }
    public List<Tratamiento> findByPaciente(Long pacienteId) { return repository.findByPacienteId(pacienteId); }
    public List<Tratamiento> findByTerapeuta(Long terapeutaId) { return repository.findByTerapeutaId(terapeutaId); }
    public Tratamiento save(Tratamiento t) {
        // Un paquete no puede NACER completado: no se ha dado ni una sesion.
        exigirQueCompletadoTengaSentido(t, 0);
        return repository.save(t);
    }

    /**
     * "Completado" significa que las sesiones se dieron, no que el paquete se pago.
     *
     * Se puso aqui porque en produccion aparecio un paquete en COMPLETADO con 0 de 10 sesiones
     * atendidas y 450 de 450 cobrados: alguien eligio ese estado entendiendo "pago completo".
     * Es una lectura razonable del nombre, y mientras el desplegable lo ofrezca va a repetirse,
     * asi que el que no cuadra no se guarda.
     *
     * Para cerrar un paquete que el paciente dejo a medias estan PAUSADO y CANCELADO, que es lo
     * que de verdad paso en esos casos.
     */
    private void exigirQueCompletadoTengaSentido(Tratamiento t, long atendidas) {
        boolean completado = t.getEstado() != null && "COMPLETADO".equals(t.getEstado().getKey());
        if (!completado) return;
        int total = t.getTotalSesiones() != null ? t.getTotalSesiones() : 0;
        if (atendidas >= total && total > 0) return;
        throw new IllegalArgumentException(
                "No se puede marcar como Completado: van " + atendidas + " de " + total
              + " sesiones atendidas. \"Completado\" quiere decir que las sesiones ya se dieron, "
              + "no que el paquete este pagado. Si el paciente lo dejo, usa Pausado o Cancelado.");
    }

    /**
     * Las citas sueltas del paciente que se podrian meter en este paquete.
     *
     * Se consulta antes de crearlo, con el terapeuta y el tipo ya elegidos, para no obligar a
     * nadie a borrar citas y volver a crearlas dentro del paquete — que es lo que se hacia.
     */
    @Transactional(readOnly = true)
    public List<Cita> absorbibles(Long pacienteId, Long terapeutaId, Long tipoTerapiaId) {
        if (pacienteId == null || terapeutaId == null || tipoTerapiaId == null) return List.of();
        return citaRepository.absorbiblesEnPaquete(pacienteId, terapeutaId, tipoTerapiaId,
                java.time.LocalDate.now().atStartOfDay(), EstadosDeCita.YA_NO_ES_UNA_SESION_POR_DAR);
    }

    /**
     * Mete citas que ya existian dentro del paquete, como sesiones suyas.
     *
     * Cada cita pasa a colgar de una sesion nueva y toma el precio por sesion del paquete. Lo
     * segundo es a peticion del negocio y tiene sentido: al agruparlas se acuerda un precio, y
     * si cada una conservara el suyo el paquete costaria una cosa y sus sesiones otra.
     *
     * Solo se tocan citas SIN dinero. En cuanto hay un pago apuntando a la cita, ese dinero
     * esta ligado a ella y no al paquete: absorberla dejaria el pago colgando de una sesion
     * cuyo cobro vive en otro sitio, que es exactamente la clase de descuadre que costo la
     * semana pasada. Se valida aqui y no solo en la consulta porque entre que se lista y se
     * confirma puede entrar un pago.
     */
    @Transactional
    public int absorber(Long tratamientoId, List<Long> citaIds) {
        if (citaIds == null || citaIds.isEmpty()) return 0;
        Tratamiento t = repository.findById(tratamientoId)
                .orElseThrow(() -> new IllegalArgumentException("Paquete no encontrado: " + tratamientoId));

        List<Sesion> sesiones = sesionRepository.findByTratamientoId(tratamientoId);
        int total = t.getTotalSesiones() != null ? t.getTotalSesiones() : 0;
        if (sesiones.size() + citaIds.size() > total) {
            throw new IllegalArgumentException("No caben: el paquete es de " + total + " sesiones, ya tiene "
                    + sesiones.size() + " y se intentan añadir " + citaIds.size() + ".");
        }

        int numero = sesiones.stream().map(Sesion::getNumero).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0);
        int metidas = 0;

        for (Long citaId : citaIds) {
            Cita c = citaRepository.findById(citaId)
                    .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + citaId));
            exigirQueSeaAbsorbible(c, t);

            Sesion s = new Sesion();
            s.setTratamiento(t);
            s.setNumero(++numero);
            s.setEstado(sesiones.isEmpty() ? estadoSesionPorDefecto() : sesiones.get(0).getEstado());
            Sesion guardada = sesionRepository.save(s);

            c.setSesion(guardada);
            if (t.getPrecioPorSesion() != null) c.setPrecio(t.getPrecioPorSesion());
            citaRepository.save(c);

            guardada.setCitaActiva(c);
            sesionRepository.save(guardada);
            metidas++;
        }
        return metidas;
    }

    private void exigirQueSeaAbsorbible(Cita c, Tratamiento t) {
        String quien = "La cita #" + c.getId();
        if (Boolean.TRUE.equals(c.getEliminado())) throw new IllegalArgumentException(quien + " esta eliminada.");
        if (c.getSesion() != null) throw new IllegalArgumentException(quien + " ya pertenece a un paquete.");
        if (c.getPaciente() == null || t.getPaciente() == null
                || !c.getPaciente().getId().equals(t.getPaciente().getId())) {
            throw new IllegalArgumentException(quien + " es de otro paciente.");
        }
        String estado = c.getEstado() != null ? c.getEstado().getKey() : "";
        if (EstadosDeCita.yaNoEsUnaSesionPorDar(estado)) {
            throw new IllegalArgumentException(quien + " esta en estado " + estado + " y ya no es una sesion por dar.");
        }
        // Tres formas de preguntar lo mismo, porque las tres pueden fallar por separado:
        // el importe cobrado, la existencia de un pago, y lo que la cita DICE de su pago.
        // La tercera parece redundante y no lo es: una cita puede quedar marcada PAGADA o
        // DESCONTADA sin importe ni pago detras (un pago borrado a medias, un arreglo a mano),
        // y absorberla la metería en el paquete dando por saldada una sesion que nadie pago.
        // En el volcado del 07/10 no hay ninguna asi; se comprueba igual porque "no hay hoy"
        // no es lo mismo que "no puede haber".
        boolean tieneDinero = c.getMontoPagado() != null
                && c.getMontoPagado().compareTo(java.math.BigDecimal.ZERO) > 0;
        if (tieneDinero || !pagoRepository.findByCitaId(c.getId()).isEmpty()) {
            throw new IllegalArgumentException(quien + " ya tiene dinero cobrado. Cobra el paquete aparte o "
                    + "elimina ese pago antes de meterla.");
        }
        String estadoPago = c.getEstadoPago() != null ? c.getEstadoPago().getKey() : "SIN_PAGO";
        if (!"SIN_PAGO".equals(estadoPago)) {
            throw new IllegalArgumentException(quien + " figura como " + estadoPago
                    + ", asi que no entra en un paquete. Solo se pueden meter citas sin ningun pago.");
        }
    }

    /** El estado con el que nacen las sesiones. Mismo criterio que usa CitaService. */
    private CatEstadoSesion estadoSesionPorDefecto() {
        return catEstadoSesionRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay estados de sesion en el catalogo."));
    }

    /** Cuantas citas de este paquete estan ASISTIDA. */
    private long sesionesAtendidas(Long tratamientoId) {
        List<Long> ids = sesionRepository.findByTratamientoId(tratamientoId).stream().map(Sesion::getId).toList();
        if (ids.isEmpty()) return 0;
        return citaRepository.findBySesionIdIn(ids).stream()
                .filter(c -> c.getEstado() != null && "ASISTIDA".equals(c.getEstado().getKey()))
                .count();
    }

    /**
     * El formulario de edición solo expone paciente/terapeuta/tipo/estado/sesiones/precio/fecha/notas
     * — sesionesAtendidas, totalCobrado y saldoAFavor NO vienen en ese request y los maneja la propia
     * app (al completar sesiones o registrar pagos), así que no se tocan aquí: sobrescribirlos con lo
     * que traiga (o no traiga) el body los dejaría en null y viola la restricción NOT NULL de la BD.
     */
    public Optional<Tratamiento> update(Long id, Tratamiento data) {
        return repository.findById(id).map(e -> {
            exigirQueCompletadoTengaSentido(data, sesionesAtendidas(id));
            e.setPaciente(data.getPaciente());
            e.setTerapeuta(data.getTerapeuta());
            e.setTipoTerapia(data.getTipoTerapia());
            e.setNombre(data.getNombre());
            e.setTotalSesiones(data.getTotalSesiones());
            e.setPrecioPorSesion(data.getPrecioPorSesion());
            e.setEstado(data.getEstado());
            e.setFechaInicio(data.getFechaInicio());
            e.setNotas(data.getNotas());
            return repository.save(e);
        });
    }

    /**
     * Borra el paquete con sus sesiones y las citas que cuelgan de ellas.
     *
     * Antes se negaba si el paquete tenia sesiones — y TODO paquete tiene sesiones, se crean con
     * el. O sea: no habia forma de borrar ninguno, ni el que se acababa de crear por error con
     * el paciente equivocado. Lo unico que de verdad no se puede tirar es la historia: si ya
     * entro dinero o si alguna sesion se atendio, hay que resolver eso antes.
     *
     * Hay un ciclo entre citas y sesiones (citas.sesion_id y sesiones.cita_activa_id), asi que
     * se sueltan las dos referencias antes de borrar nada.
     */
    @Transactional
    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;

        if (!pagoRepository.findByTratamientoId(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "No se puede eliminar: este paquete ya tiene pagos registrados. Elimina primero esos pagos desde Pagos.");
        }

        List<Sesion> sesiones = sesionRepository.findByTratamientoId(id);
        List<Long> sesionIds = sesiones.stream().map(Sesion::getId).toList();
        List<Cita> citas = sesionIds.isEmpty() ? List.of() : citaRepository.findBySesionIdIn(sesionIds);

        boolean hayAtendidas = citas.stream().anyMatch(c ->
                c.getEstado() != null && "ASISTIDA".equals(c.getEstado().getKey()));
        if (hayAtendidas) {
            throw new IllegalArgumentException(
                    "No se puede eliminar: este paquete ya tiene sesiones atendidas. Anulalo en vez de borrarlo.");
        }
        boolean hayCobradas = citas.stream().anyMatch(c ->
                c.getMontoPagado() != null && c.getMontoPagado().compareTo(java.math.BigDecimal.ZERO) > 0);
        if (hayCobradas) {
            throw new IllegalArgumentException(
                    "No se puede eliminar: alguna sesion de este paquete ya tiene dinero cobrado. Anulalo en vez de borrarlo.");
        }

        // Se rompe el ciclo citas <-> sesiones antes de borrar ninguna de las dos.
        for (Sesion s : sesiones) { s.setCitaActiva(null); }
        sesionRepository.saveAll(sesiones);
        for (Cita c : citas) { c.setSesion(null); }
        citaRepository.saveAll(citas);

        citaRepository.deleteAll(citas);
        sesionRepository.deleteAll(sesiones);
        repository.deleteById(id);
        return true;
    }

    /**
     * Cuántas sesiones ya tienen cita creada (de las planificadas) y cuántas de esas
     * ya están pagadas vs pendientes de pago. Se calcula solo cuando se pide (no en el
     * listado paginado) para no cargarle joins a la consulta pesada.
     */
    @Transactional(readOnly = true)
    public TratamientoCoberturaDTO cobertura(Long tratamientoId) {
        List<Sesion> sesiones = sesionRepository.findByTratamientoId(tratamientoId);
        int creadas = sesiones.size();
        int pagadas = (int) sesiones.stream()
                .filter(s -> s.getCitaActiva() != null
                        && s.getCitaActiva().getEstadoPago() != null
                        && "PAGADA".equals(s.getCitaActiva().getEstadoPago().getKey()))
                .count();
        return new TratamientoCoberturaDTO(creadas, pagadas, creadas - pagadas);
    }

    @Transactional(readOnly = true)
    public List<SesionDTO> getSesionesByTratamiento(Long tratamientoId) {
        return sesionRepository.findByTratamientoIdWithCita(tratamientoId)
                .stream()
                .map(this::toSesionDTO)
                .collect(Collectors.toList());
    }

    public TratamientoDTO toDTO(Tratamiento t) {
        TratamientoDTO dto = new TratamientoDTO();
        dto.setId(t.getId());
        dto.setNombre(t.getNombre());
        dto.setTotalSesiones(t.getTotalSesiones());
        dto.setSesionesAtendidas(t.getSesionesAtendidas());
        dto.setSesionesPendientes(t.getSesionesPendientes());
        dto.setMontoTotal(t.getMontoTotal());
        dto.setPrecioPorSesion(t.getPrecioPorSesion());
        dto.setTotalCobrado(t.getTotalCobrado());
        dto.setFechaInicio(t.getFechaInicio());
        dto.setNotas(t.getNotas());

        if (t.getPaciente() != null) {
            Paciente p = t.getPaciente();
            dto.setPacienteId(p.getId());
            dto.setPacienteNombre(p.getNombre());
            dto.setPacienteApellido(p.getApellido());
            dto.setPacienteDni(p.getDni());
            dto.setPacienteTelefono(p.getTelefono());
            // El saldo a favor es del paciente, no de este paquete puntual (ver PagoService).
            dto.setSaldoAFavor(p.getSaldoAFavor());
        }

        if (t.getTerapeuta() != null && t.getTerapeuta().getUsuario() != null) {
            dto.setTerapeutaId(t.getTerapeuta().getId());
            Usuario u = t.getTerapeuta().getUsuario();
            dto.setTerapeutaNombre(u.getNombre() + " " + u.getApellido());
        }

        if (t.getTipoTerapia() != null) {
            dto.setTipoTerapiaKey(t.getTipoTerapia().getKey());
            dto.setTipoTerapiaNombre(t.getTipoTerapia().getNombre());
        }

        if (t.getEstado() != null) {
            dto.setEstadoKey(t.getEstado().getKey());
            dto.setEstadoNombre(t.getEstado().getNombre());
            dto.setEstadoColor(t.getEstado().getColorHex());
        }

        return dto;
    }

    private SesionDTO toSesionDTO(Sesion s) {
        SesionDTO dto = new SesionDTO();
        dto.setId(s.getId());
        dto.setNumero(s.getNumero());

        if (s.getEstado() != null) {
            SesionDTO.EstadoInfo e = new SesionDTO.EstadoInfo();
            e.setId(s.getEstado().getId());
            e.setKey(s.getEstado().getKey());
            e.setNombre(s.getEstado().getNombre());
            e.setColorHex(s.getEstado().getColorHex());
            dto.setEstado(e);
        }

        Cita cita = s.getCitaActiva();
        if (cita != null) {
            SesionDTO.CitaActivaInfo ca = new SesionDTO.CitaActivaInfo();
            ca.setId(cita.getId());
            ca.setFechaInicio(cita.getFechaInicio());
            ca.setDuracionMinutos(cita.getDuracionMinutos());

            if (cita.getEstado() != null) {
                SesionDTO.EstadoInfo ce = new SesionDTO.EstadoInfo();
                ce.setId(cita.getEstado().getId());
                ce.setKey(cita.getEstado().getKey());
                ce.setNombre(cita.getEstado().getNombre());
                ce.setColorHex(cita.getEstado().getColorHex());
                ca.setEstado(ce);
            }

            if (cita.getModalidad() != null) {
                SesionDTO.ModalidadInfo m = new SesionDTO.ModalidadInfo();
                m.setKey(cita.getModalidad().getKey());
                m.setNombre(cita.getModalidad().getNombre());
                ca.setModalidad(m);
            }

            if (cita.getEstadoPago() != null) {
                ca.setEstadoPagoKey(cita.getEstadoPago().getKey());
                ca.setEstadoPagoNombre(cita.getEstadoPago().getNombre());
                ca.setEstadoPagoColor(cita.getEstadoPago().getColor());
            }

            ca.setPrecio(cita.getPrecio());
            ca.setMontoPagado(cita.getMontoPagado());

            dto.setCitaActiva(ca);
        }

        return dto;
    }
}
