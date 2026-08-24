package com.therateam.therateam.service;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.model.CatRol;
import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.CatRolRepository;
import com.therateam.therateam.repository.PacienteRepository;
import com.therateam.therateam.repository.SedeRepository;
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

    public List<Paciente> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Paciente> findAllPaged(Pageable pageable, String nombre, String dni, String correo,
                                        Long sedeId, Boolean activo, Long terapeutaId) {
        return repository.buscarPaged(blankToNull(nombre), blankToNull(dni), blankToNull(correo),
                sedeId, activo, terapeutaId, pageable);
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
     * Si se conoce la fecha de nacimiento y el paciente es menor de 18, los datos del apoderado
     * (quien responde por él) son obligatorios. Si no se conoce la fecha de nacimiento (ej. alta
     * rápida desde Citas, que no la pide) no se puede determinar la edad — no bloquea ese flujo.
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

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
