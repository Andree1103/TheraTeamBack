package com.therateam.therateam.service;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.model.CatRol;
import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.AsientoRepository;
import com.therateam.therateam.repository.CatRolRepository;
import com.therateam.therateam.repository.CitaHistorialRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PacienteRepository;
import com.therateam.therateam.repository.PagoRepository;
import com.therateam.therateam.repository.SaldoMovimientoRepository;
import com.therateam.therateam.repository.SedeRepository;
import com.therateam.therateam.repository.TratamientoRepository;
import com.therateam.therateam.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final CatRolRepository catRolRepository;
    private final SedeRepository sedeRepository;
    private final PasswordEncoder passwordEncoder;
    private final CitaRepository citaRepository;
    private final PagoRepository pagoRepository;
    private final TratamientoRepository tratamientoRepository;
    private final SaldoMovimientoRepository saldoMovimientoRepository;
    private final AsientoRepository asientoRepository;
    private final CitaHistorialRepository citaHistorialRepository;

    public List<Paciente> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Paciente> findAllPaged(Pageable pageable, String nombre, String dni, String correo,
                                        Long sedeId, Boolean activo, Long terapeutaId) {
        return findAllPaged(pageable, nombre, dni, correo, sedeId, activo, terapeutaId, null, null);
    }

    /** Igual que el anterior, acotando ademas por fecha de alta del paciente (dias completos). */
    @Transactional(readOnly = true)
    public Page<Paciente> findAllPaged(Pageable pageable, String nombre, String dni, String correo,
                                        Long sedeId, Boolean activo, Long terapeutaId,
                                        java.time.LocalDate creadoDesde, java.time.LocalDate creadoHasta) {
        return repository.buscarPaged(blankToNull(nombre), blankToNull(dni), blankToNull(correo),
                sedeId, activo, terapeutaId,
                creadoDesde != null ? creadoDesde.atStartOfDay() : null,
                creadoHasta != null ? creadoHasta.atTime(23, 59, 59) : null,
                pageable);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Reporte de adelantos: pacientes con saldo a favor disponible. */
    @Transactional(readOnly = true)
    public Page<Paciente> findConSaldoAFavorPaged(Pageable pageable, String nombre) {
        return repository.findConSaldoAFavor(blankToNull(nombre), pageable);
    }

    public Optional<Paciente> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<Paciente> findByDni(String dni) {
        return repository.findByDni(dni);
    }

    /** Crea el paciente y, junto con él, su cuenta de acceso (rol PACIENTE, password = su DNI). */
    @Transactional
    public Paciente save(Paciente paciente) {
        // Obligatoria al crear: sin ella no hay forma de saber si es menor, y la exigencia del
        // apoderado se esquivaba dejando el campo vacio. Solo aplica al alta -- editar un
        // paciente antiguo que quedo sin fecha sigue permitido (ver update).
        if (paciente.getFechaNacimiento() == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }
        validarDniUnico(paciente.getDni(), null);
        validarApoderadoSiEsMenor(paciente);
        if (paciente.getSede() == null) {
            sedeRepository.findFirstByActivoTrueOrderByIdAsc().ifPresent(paciente::setSede);
        }
        if (paciente.getUsuario() == null) {
            paciente.setUsuario(crearUsuarioParaPaciente(paciente));
        }
        return repository.save(paciente);
    }

    @Transactional
    public Optional<Paciente> update(Long id, Paciente data) {
        validarDniUnico(data.getDni(), id);
        validarApoderadoSiEsMenor(data);
        return repository.findById(id).map(existing -> {
            existing.setNombre(data.getNombre());
            existing.setApellido(data.getApellido());
            existing.setDni(data.getDni());
            // A quien no puede ver teléfonos se le manda null en el GET, así que su formulario lo
            // devuelve vacío: guardarlo tal cual borraba el número real al editar cualquier otro
            // campo. Solo lo escribe quien de verdad lo está viendo.
            if (SecurityUtils.puedeVerTelefonoPacientes()) {
                existing.setTelefono(data.getTelefono());
            }
            existing.setCorreo(data.getCorreo());
            existing.setFechaNacimiento(data.getFechaNacimiento());
            existing.setDniApoderado(data.getDniApoderado());
            existing.setNombreApoderado(data.getNombreApoderado());
            existing.setCelularApoderado(data.getCelularApoderado());
            existing.setActivo(data.getActivo());
            // Completa la cuenta de acceso si el paciente venía sin correo (de antes de este cambio)
            // y recién ahora se le está agregando uno.
            if (existing.getUsuario() == null && existing.getCorreo() != null && !existing.getCorreo().isBlank()) {
                existing.setUsuario(crearUsuarioParaPaciente(existing));
            }
            return repository.save(existing);
        });
    }

    /**
     * El DNI identifica al paciente: repetirlo crea dos fichas de la misma persona, con sus citas
     * y sus pagos partidos entre ambas. La base ya lo impedía con una restricción UNIQUE, pero el
     * error llegaba a pantalla como "Los datos enviados no son válidos o están incompletos", sin
     * decir cuál era el dato ni con quién chocaba. Se valida antes para poder nombrar al paciente
     * que ya lo tiene y que la recepción lo busque en vez de volver a crearlo.
     *
     * @param idActual al editar, el propio paciente — para que guardar sin cambiarle el DNI no
     *                 se choque consigo mismo.
     */
    private void validarDniUnico(String dni, Long idActual) {
        if (isBlank(dni)) return;
        repository.findByDni(dni.trim()).ifPresent(otro -> {
            if (idActual != null && idActual.equals(otro.getId())) return;
            throw new IllegalArgumentException(
                    "Ya existe un paciente con el DNI " + dni.trim() + ": "
                    + otro.getNombre() + " " + otro.getApellido() + ".");
        });
    }

    /**
     * Si el paciente es menor de 18, los datos del apoderado (quien responde por él) son
     * obligatorios. Al crear, la fecha siempre viene (save() la exige). Al editar puede faltar en
     * pacientes antiguos, cargados antes de que fuera obligatoria: ahí no se puede determinar la
     * edad y no se bloquea, para no dejar esos registros imposibles de corregir.
     */
    private void validarApoderadoSiEsMenor(Paciente p) {
        if (p.getFechaNacimiento() == null) return;
        int edad = Period.between(p.getFechaNacimiento(), LocalDate.now()).getYears();
        if (edad >= 18) return;
        boolean faltaAlgo = isBlank(p.getDniApoderado()) || isBlank(p.getNombreApoderado()) || isBlank(p.getCelularApoderado());
        if (faltaAlgo) {
            throw new IllegalArgumentException(
                    "El paciente es menor de edad (" + edad + " años) — el DNI, nombre y celular del apoderado son obligatorios.");
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    /**
     * Cuenta de login del paciente: email = su correo, password inicial = su DNI (se lo puede
     * cambiar él mismo más adelante cuando exista el portal de pacientes). Requiere ambos datos.
     */
    private Usuario crearUsuarioParaPaciente(Paciente paciente) {
        if (paciente.getCorreo() == null || paciente.getCorreo().isBlank()) {
            throw new IllegalArgumentException("El correo del paciente es obligatorio (se usa para su cuenta de acceso).");
        }
        if (paciente.getDni() == null || paciente.getDni().isBlank()) {
            throw new IllegalArgumentException("El DNI del paciente es obligatorio (se usa como contraseña inicial de su cuenta).");
        }
        if (usuarioRepository.findByEmail(paciente.getCorreo()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una cuenta con el correo " + paciente.getCorreo() + ".");
        }
        CatRol rolPaciente = catRolRepository.findByKey("PACIENTE")
                .orElseThrow(() -> new IllegalStateException("No existe el rol PACIENTE — falta la migración inicial."));

        Usuario usuario = new Usuario();
        usuario.setNombre(paciente.getNombre());
        usuario.setApellido(paciente.getApellido());
        usuario.setEmail(paciente.getCorreo());
        usuario.setPasswordHash(passwordEncoder.encode(paciente.getDni()));
        usuario.setRol(rolPaciente);
        usuario.setActivo(true);
        usuario.setCitasSoloPropias(false);
        usuario.setCitasPuedeCrear(true);
        sedeRepository.findFirstByActivoTrueOrderByIdAsc().ifPresent(usuario::setSede);
        return usuarioRepository.save(usuario);
    }

    /**
     * Dar de baja al paciente, o volver a darlo de alta.
     *
     * Es lo que de verdad se quiere casi siempre que alguien pulsa Eliminar: el paciente dejo de
     * venir, no hay que borrar su historia. Inactivo lo saca de los listados y de las busquedas
     * con las que se trabaja —agendar, cobrar, abrir un paquete—, y su ficha sigue entera.
     */
    @Transactional
    public Optional<Paciente> cambiarActivo(Long id, boolean activo) {
        return repository.findById(id).map(p -> {
            p.setActivo(activo);
            return repository.save(p);
        });
    }

    /**
     * Borrar un paciente es para la ficha creada por error, no para el que ya tiene historia.
     *
     * Ninguna de las tablas que cuelgan del paciente —citas, pagos, paquetes, movimientos de
     * saldo, asientos del libro— tiene ON DELETE: la base rechaza el borrado y hasta ahora eso
     * salia a pantalla como "Error al eliminar el paciente", sin decir que lo impedia ni que
     * hacer en su lugar. Y esta bien que lo rechace: esos registros son la caja y el libro de un
     * dia que ya se cerro, no se pueden tirar porque alguien deje de venir. Lo que faltaba era
     * decirlo. Para ese caso esta el estado Inactivo, que es lo que de verdad se quiere.
     */
    @Transactional
    public boolean delete(Long id) {
        Paciente paciente = repository.findById(id).orElse(null);
        if (paciente == null) return false;

        exigirQueNoTengaHistoria(paciente);

        Usuario cuenta = paciente.getUsuario();
        repository.deleteById(id);
        borrarCuentaDe(cuenta);
        return true;
    }

    private void exigirQueNoTengaHistoria(Paciente paciente) {
        Long id = paciente.getId();

        // Lo que la recepción reconoce de inmediato, porque lo ve en la ficha del paciente.
        List<String> tiene = new java.util.ArrayList<>();
        agregarSiHay(tiene, citaRepository.countByPacienteId(id), "cita", "citas");
        agregarSiHay(tiene, pagoRepository.countByPacienteId(id), "pago", "pagos");
        agregarSiHay(tiene, tratamientoRepository.countByPacienteId(id), "paquete", "paquetes");

        // Y lo que cuelga por dentro. Casi siempre viene acompañado de un pago, y entonces
        // nombrarlo solo añade ruido; se dice únicamente si es lo único que queda, para que el
        // mensaje nunca salga vacío y vuelva a caer en el error crudo de la base.
        if (tiene.isEmpty()) {
            agregarSiHay(tiene, saldoMovimientoRepository.countByPacienteId(id),
                    "movimiento de saldo a favor", "movimientos de saldo a favor");
            agregarSiHay(tiene, asientoRepository.countByPacienteId(id),
                    "apunte en el libro de movimientos", "apuntes en el libro de movimientos");
        }
        if (tiene.isEmpty()) return;

        throw new IllegalArgumentException(
                "No se puede eliminar a " + paciente.getNombre() + " " + paciente.getApellido()
                + ": tiene " + enumerar(tiene) + ". Esa historia es parte de la caja y del libro, "
                + "y borrarla descuadraría los dos. Si ya no viene, dale de baja: su ficha se "
                + "queda entera, pero sale de los listados y de las búsquedas.");
    }

    private static void agregarSiHay(List<String> destino, long cuantos, String singular, String plural) {
        if (cuantos > 0) destino.add(cuantos + " " + (cuantos == 1 ? singular : plural));
    }

    /** "3 citas, 2 pagos y 1 paquete" — como se lee en voz alta, no separado por comas hasta el final. */
    private static String enumerar(List<String> partes) {
        if (partes.size() == 1) return partes.get(0);
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    /**
     * La cuenta de acceso se crea con el paciente, asi que se va con el.
     *
     * Si se quedara, su correo —que sale del DNI— seguiria ocupado, y volver a dar de alta a la
     * misma persona fallaria con "Ya existe una cuenta con ese correo" sin que nadie vea de
     * donde sale. Solo se borra si no dejo rastro en el historial de citas; si lo dejo, la
     * cuenta se queda para que ese rastro siga teniendo nombre.
     */
    private void borrarCuentaDe(Usuario cuenta) {
        if (cuenta == null) return;
        if (citaHistorialRepository.countByUsuarioId(cuenta.getId()) > 0) return;
        usuarioRepository.deleteById(cuenta.getId());
    }
}
