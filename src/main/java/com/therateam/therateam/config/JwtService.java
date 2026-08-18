package com.therateam.therateam.config;

import com.therateam.therateam.model.RolModuloPermiso;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.TerapeutaRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Emite y valida los JWT — el token lleva embebidos el rol y los módulos permitidos para no tener
 * que consultar la BD en cada request (a costa de que un cambio de permisos solo aplica en el
 * próximo login/token nuevo — trade-off aceptado por simplicidad y performance). */
@Component
@RequiredArgsConstructor
public class JwtService {

    private final TerapeutaRepository terapeutaRepository;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:36000000}") // 10 horas por default
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(Usuario usuario) {
        List<RolModuloPermiso> permisos = usuario.getRol() != null ? usuario.getRol().getPermisos() : List.of();

        // "modulos": para el guard de navegación del frontend (acceso = la fila existe).
        Set<String> modulos = permisos.stream().map(p -> p.getModulo().getKey()).collect(Collectors.toSet());
        // "permisos": para gating fino de botones crear/editar/eliminar por módulo.
        List<Map<String, Object>> permisosClaim = permisos.stream().map(p -> Map.<String, Object>of(
                "modulo", p.getModulo().getKey(),
                "crear", p.isCrear(), "editar", p.isEditar(), "eliminar", p.isEliminar()
        )).toList();

        Long terapeutaId = terapeutaRepository.findByUsuarioId(usuario.getId())
                .map(Terapeuta::getId).orElse(null);

        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getEmail())
                .claim("nombre", usuario.getNombre() + " " + usuario.getApellido())
                .claim("rol", usuario.getRol() != null ? usuario.getRol().getKey() : null)
                .claim("modulos", modulos)
                .claim("permisos", permisosClaim)
                .claim("terapeutaId", terapeutaId)
                .claim("citasSoloPropias", Boolean.TRUE.equals(usuario.getCitasSoloPropias()))
                .claim("citasPuedeCrear", !Boolean.FALSE.equals(usuario.getCitasPuedeCrear()))
                .claim("pacientesVerTelefono", usuario.getRol() != null && Boolean.TRUE.equals(usuario.getRol().getPacientesVerTelefono()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
