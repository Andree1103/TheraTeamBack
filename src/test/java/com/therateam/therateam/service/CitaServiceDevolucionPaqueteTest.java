package com.therateam.therateam.service;

import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.model.Sesion;
import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.repository.CatEstadoPagoCitaRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PacienteRepository;
import com.therateam.therateam.repository.PagoRepository;
import com.therateam.therateam.repository.TratamientoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Un reembolso no puede crear una deuda.
 *
 * La deuda de un paquete se lee en todas las pantallas como «lo que vale menos lo cobrado», y lo
 * que vale son sus sesiones por el precio. Devolver el dinero de una sesion baja lo cobrado; si
 * el paquete siguiera diciendo que tiene las mismas sesiones, pasaria a reclamar justo el importe
 * que se acaba de devolver. Le paso a un paciente de verdad: se le entregaron sus S/ 50 y la
 * ficha empezo a decir que debia S/ 50.
 *
 * Esta clase es el seguro de que no vuelve. La bateria db/pruebas-dinero.sh recorre lo mismo de
 * punta a punta contra una copia real; esto corre con el build, sin base de datos, y por eso
 * puede fallar antes de que nadie despliegue nada.
 *
 * Los mocks que no se usan se dejan pasar (LENIENT) a proposito: CitaService arrastra 23
 * dependencias y aqui solo importa por donde se mueve el dinero de un paquete.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CitaServiceDevolucionPaqueteTest {

    @Mock private CitaRepository citaRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private TratamientoRepository tratamientoRepository;
    @Mock private CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private PagoService pagoService;
    @Mock private LibroService libro;
    @Mock private SaldoMovimientoService saldoMovimientoService;

    @InjectMocks private CitaService service;

    private static final long PACIENTE_ID = 7L;

    @Test
    void devolver_el_dinero_de_una_sesion_deja_el_paquete_sin_deuda() {
        Tratamiento paquete = paquete(2, "50", "100");   // 2 sesiones de 50, cobrado entero
        Cita cita = sesionDelPaquete(paquete, 1, "50");

        service.revertirDinero(cita, true, null, "anulación");

        assertThat(paquete.getTotalSesiones()).isEqualTo(1);
        assertThat(paquete.getTotalCobrado()).isEqualByComparingTo("50");
        assertThat(deuda(paquete)).isEqualByComparingTo("0");
    }

    @Test
    void dejarlo_a_favor_tampoco_deja_deuda() {
        // El peor de los dos: antes la ficha decia a la vez "S/ 50 a favor" y "S/ 50 de deuda".
        lenient().when(pagoService.elPaqueteRecibioDinero(any())).thenReturn(true);
        Tratamiento paquete = paquete(2, "50", "100");
        Cita cita = sesionDelPaquete(paquete, 1, "50");

        service.revertirDinero(cita, false, null, "anulación");

        assertThat(paquete.getTotalSesiones()).isEqualTo(1);
        assertThat(deuda(paquete)).isEqualByComparingTo("0");
    }

    @Test
    void un_cobro_parcial_devuelto_deja_por_cobrar_la_sesion_que_sigue_en_pie() {
        // Baja de una en una, no por el importe: lo que cambia es que esa sesion no se va a dar.
        // 2 sesiones de 50 con 30 cobrados sobre la primera; al devolver los 30 quedan por cobrar
        // los 50 de la otra sesion. Antes quedaban 100 — las dos, una de ellas ya devuelta.
        Tratamiento paquete = paquete(2, "50", "30");
        Cita cita = sesionDelPaquete(paquete, 1, "30");

        service.revertirDinero(cita, true, null, "anulación");

        assertThat(paquete.getTotalSesiones()).isEqualTo(1);
        assertThat(paquete.getTotalCobrado()).isEqualByComparingTo("0");
        assertThat(deuda(paquete)).isEqualByComparingTo("50");
    }

    @Test
    void anular_una_sesion_que_nadie_pago_no_encoge_el_paquete() {
        // Sin reembolso no hay nada que justifique encogerlo: el hueco queda libre para volver a
        // agendarlo, que es como se ha usado siempre.
        Tratamiento paquete = paquete(2, "50", "0");
        Cita cita = sesionDelPaquete(paquete, 1, "0");

        service.revertirDinero(cita, true, null, "anulación");

        assertThat(paquete.getTotalSesiones()).isEqualTo(2);
        assertThat(paquete.getTotalCobrado()).isEqualByComparingTo("0");
    }

    @Test
    void nunca_baja_por_debajo_de_las_sesiones_ya_atendidas() {
        // «Sesiones pendientes» se calcula restando las dos: dejarlo en negativo diria un
        // disparate —un paquete que debe menos sesiones de las que ya dio.
        Tratamiento paquete = paquete(2, "50", "100");
        paquete.setSesionesAtendidas(2);
        Cita cita = sesionDelPaquete(paquete, 1, "50");

        service.revertirDinero(cita, true, null, "anulación");

        assertThat(paquete.getTotalSesiones()).isEqualTo(2);
    }

    // ── Armado ────────────────────────────────────────────────────────────────

    private Tratamiento paquete(int sesiones, String precio, String cobrado) {
        Paciente paciente = new Paciente();
        paciente.setId(PACIENTE_ID);
        paciente.setSaldoAFavor(BigDecimal.ZERO);
        lenient().when(pacienteRepository.findById(PACIENTE_ID)).thenReturn(Optional.of(paciente));

        Tratamiento t = new Tratamiento();
        t.setId(99L);
        t.setNombre("Paquete de prueba");
        t.setPaciente(paciente);
        t.setTotalSesiones(sesiones);
        t.setSesionesAtendidas(0);
        t.setPrecioPorSesion(new BigDecimal(precio));
        t.setTotalCobrado(new BigDecimal(cobrado));
        return t;
    }

    /** Una cita del paquete con `pagado` aplicado encima, como la deja un cobro. */
    private Cita sesionDelPaquete(Tratamiento paquete, int numero, String pagado) {
        Sesion sesion = new Sesion();
        sesion.setId(500L + numero);
        sesion.setNumero(numero);
        sesion.setTratamiento(paquete);

        Cita cita = new Cita();
        cita.setId(1000L + numero);
        cita.setPaciente(paquete.getPaciente());
        cita.setSesion(sesion);
        cita.setPrecio(paquete.getPrecioPorSesion());
        cita.setMontoPagado(new BigDecimal(pagado));
        return cita;
    }

    /** Lo que las pantallas enseñan como deuda del paquete. */
    private BigDecimal deuda(Tratamiento t) {
        BigDecimal vale = t.getPrecioPorSesion().multiply(BigDecimal.valueOf(t.getTotalSesiones()));
        return vale.subtract(t.getTotalCobrado()).max(BigDecimal.ZERO);
    }
}
