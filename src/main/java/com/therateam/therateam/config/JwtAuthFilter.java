package com.therateam.therateam.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Lee el Bearer token de cada request, lo valida y arma las authorities de Spring Security a
 * partir de sus claims (rol → ROLE_X, módulos → MODULO_Y). Si el token falta o es inválido,
 * simplemente no autentica — @PreAuthorize / authorizeHttpRequests se encargan de bloquear
 * según corresponda (no lanza 401 aquí para no interferir con /api/auth/** que va sin token).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                String rol = claims.get("rol", String.class);
                @SuppressWarnings("unchecked")
                Collection<String> modulos = claims.get("modulos", Collection.class);

                List<GrantedAuthority> authorities = new ArrayList<>();
                if (rol != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
                if (modulos != null) {
                    modulos.forEach(m -> authorities.add(new SimpleGrantedAuthority("MODULO_" + m)));
                }
                // Permisos granulares por módulo: MODULO_X_CREAR / MODULO_X_EDITAR / MODULO_X_ELIMINAR.
                @SuppressWarnings("unchecked")
                Collection<Map<String, Object>> permisos = claims.get("permisos", Collection.class);
                if (permisos != null) {
                    for (Map<String, Object> p : permisos) {
                        String modulo = String.valueOf(p.get("modulo"));
                        if (Boolean.TRUE.equals(p.get("crear"))) authorities.add(new SimpleGrantedAuthority("MODULO_" + modulo + "_CREAR"));
                        if (Boolean.TRUE.equals(p.get("editar"))) authorities.add(new SimpleGrantedAuthority("MODULO_" + modulo + "_EDITAR"));
                        if (Boolean.TRUE.equals(p.get("eliminar"))) authorities.add(new SimpleGrantedAuthority("MODULO_" + modulo + "_ELIMINAR"));
                    }
                }
                // Tokens viejos (emitidos antes de este campo) no traen el claim: por defecto se permite crear.
                Boolean puedeCrearCitas = claims.get("citasPuedeCrear", Boolean.class);
                if (!Boolean.FALSE.equals(puedeCrearCitas)) {
                    authorities.add(new SimpleGrantedAuthority("CITAS_PUEDE_CREAR"));
                }
                // Dato sensible: se configura por ROL (Seguridad > Roles), no por usuario individual.
                Boolean puedeVerTelefono = claims.get("pacientesVerTelefono", Boolean.class);
                if (Boolean.TRUE.equals(puedeVerTelefono)) {
                    authorities.add(new SimpleGrantedAuthority("PACIENTES_VER_TELEFONO"));
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, authorities);
                // Los claims completos (terapeutaId, citasSoloPropias, etc.) quedan disponibles para
                // los controllers que necesiten filtrar resultados, no solo autorizar/denegar.
                authentication.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
