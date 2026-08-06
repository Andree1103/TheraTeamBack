package com.therateam.therateam.config;

import com.therateam.therateam.model.CatRol;
import com.therateam.therateam.model.Modulo;
import com.therateam.therateam.model.RolModuloPermiso;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.TerapeutaRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * El token es la base de todo el sistema de permisos granulares — si el subject o los claims
 * de permisos/rol quedan mal armados, toda la autorización aguas abajo queda comprometida.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock private TerapeutaRepository terapeutaRepository;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(terapeutaRepository);
        ReflectionTestUtils.setField(jwtService, "secret", "clave-de-prueba-para-tests-1234567890123456");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);
    }

    private Usuario usuarioConRol(String rolKey, boolean crear, boolean editar, boolean eliminar) {
        Modulo modulo = new Modulo();
        modulo.setKey("PACIENTES");

        RolModuloPermiso permiso = new RolModuloPermiso();
        permiso.setModulo(modulo);
        permiso.setCrear(crear);
        permiso.setEditar(editar);
        permiso.setEliminar(eliminar);

        CatRol rol = new CatRol();
        rol.setKey(rolKey);
        rol.setPermisos(List.of(permiso));

        Usuario u = new Usuario();
        u.setId(42L);
        u.setEmail("test@therateam.com");
        u.setNombre("Ana");
        u.setApellido("Gómez");
        u.setRol(rol);
        u.setCitasSoloPropias(false);
        u.setCitasPuedeCrear(true);
        return u;
    }

    @Test
    void generarToken_elSubjectEsElIdDelUsuario() {
        Usuario u = usuarioConRol("ADMIN", true, true, true);
        when(terapeutaRepository.findByUsuarioId(42L)).thenReturn(Optional.empty());

        String token = jwtService.generarToken(u);
        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("rol", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("citasPuedeCrear", Boolean.class)).isTrue();
    }

    @Test
    void generarToken_incluyeLosPermisosGranularesPorModulo() {
        Usuario u = usuarioConRol("TERAPEUTA", false, true, false);
        when(terapeutaRepository.findByUsuarioId(42L)).thenReturn(Optional.empty());

        String token = jwtService.generarToken(u);
        Claims claims = jwtService.parse(token);

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> permisos = claims.get("permisos", List.class);
        assertThat(permisos).hasSize(1);
        assertThat(permisos.get(0)).containsEntry("modulo", "PACIENTES")
                .containsEntry("crear", false)
                .containsEntry("editar", true)
                .containsEntry("eliminar", false);
    }

    @Test
    void generarToken_siElUsuarioEsTerapeuta_incluyeSuTerapeutaId() {
        Usuario u = usuarioConRol("TERAPEUTA", false, true, false);
        Terapeuta t = new Terapeuta();
        t.setId(7L);
        when(terapeutaRepository.findByUsuarioId(42L)).thenReturn(Optional.of(t));

        String token = jwtService.generarToken(u);
        Claims claims = jwtService.parse(token);

        assertThat(claims.get("terapeutaId", Integer.class)).isEqualTo(7);
    }

    @Test
    void parse_conTokenManipulado_lanzaExcepcion() {
        assertThatThrownBy(() -> jwtService.parse("esto.no.es.un.token.valido"))
                .isInstanceOf(RuntimeException.class);
    }
}
