package com.therateam.therateam.service;

import com.therateam.therateam.model.CatEstadoPagoCita;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.repository.CatEstadoPagoCitaRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PagoRepository;
import com.therateam.therateam.repository.TratamientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cubre el cálculo de saldo a favor / monto aplicado en PagoService.save() — es dinero real,
 * así que un error de redondeo o de signo aquí se traduce directo en una cuenta mal cobrada.
 */
@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock private PagoRepository repository;
    @Mock private CitaRepository citaRepository;
    @Mock private CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;
    @Mock private TratamientoRepository tratamientoRepository;

    private PagoService service;

    @BeforeEach
    void setUp() {
        service = new PagoService(repository, citaRepository, catEstadoPagoCitaRepository, tratamientoRepository);
        lenient().when(repository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Tratamiento tratamiento(BigDecimal precioPorSesion, int totalSesiones, BigDecimal totalCobrado, BigDecimal saldoAFavor) {
        Tratamiento t = new Tratamiento();
        t.setId(1L);
        t.setPrecioPorSesion(precioPorSesion);
        t.setTotalSesiones(totalSesiones);
        t.setTotalCobrado(totalCobrado);
        t.setSaldoAFavor(saldoAFavor);
        return t;
    }

    private Pago pagoParaTratamiento(Long tratamientoId, BigDecimal montoRecibido) {
        Pago p = new Pago();
        Tratamiento ref = new Tratamiento();
        ref.setId(tratamientoId);
        p.setTratamiento(ref);
        p.setMontoRecibido(montoRecibido);
        return p;
    }

    @Test
    void save_pagoParcialDePaquete_aplicaTodoYNoGeneraSaldo() {
        // Paquete de 10 sesiones a 50 c/u = 500 total, nada cobrado aún.
        Tratamiento t = tratamiento(new BigDecimal("50"), 10, BigDecimal.ZERO, BigDecimal.ZERO);
        when(tratamientoRepository.findById(1L)).thenReturn(Optional.of(t));

        Pago pago = pagoParaTratamiento(1L, new BigDecimal("200"));
        Pago resultado = service.save(pago);

        assertThat(resultado.getMontoAplicado()).isEqualByComparingTo("200");
        assertThat(resultado.getSaldoGenerado()).isEqualByComparingTo("0");
        assertThat(t.getTotalCobrado()).isEqualByComparingTo("200");
    }

    @Test
    void save_pagoQueSuperaLaDeuda_generaSaldoAFavor() {
        // Deuda pendiente = 500 - 450 = 50, pero se paga 100 -> 50 aplicado, 50 de saldo a favor.
        Tratamiento t = tratamiento(new BigDecimal("50"), 10, new BigDecimal("450"), BigDecimal.ZERO);
        when(tratamientoRepository.findById(1L)).thenReturn(Optional.of(t));

        Pago pago = pagoParaTratamiento(1L, new BigDecimal("100"));
        Pago resultado = service.save(pago);

        assertThat(resultado.getMontoAplicado()).isEqualByComparingTo("50");
        assertThat(resultado.getSaldoGenerado()).isEqualByComparingTo("50");
        assertThat(t.getTotalCobrado()).isEqualByComparingTo("500");
        assertThat(t.getSaldoAFavor()).isEqualByComparingTo("50");
    }

    @Test
    void save_usaElSaldoAFavorArrastradoAntesQueElNuevoPago() {
        // Ya tenía 30 de saldo a favor; paga 20 más -> disponible 50, deuda pendiente 500-480=20.
        Tratamiento t = tratamiento(new BigDecimal("50"), 10, new BigDecimal("480"), new BigDecimal("30"));
        when(tratamientoRepository.findById(1L)).thenReturn(Optional.of(t));

        Pago pago = pagoParaTratamiento(1L, new BigDecimal("20"));
        Pago resultado = service.save(pago);

        assertThat(resultado.getSaldoPrevio()).isEqualByComparingTo("30");
        assertThat(resultado.getMontoAplicado()).isEqualByComparingTo("20");
        assertThat(resultado.getSaldoGenerado()).isEqualByComparingTo("30");
        assertThat(t.getTotalCobrado()).isEqualByComparingTo("500");
    }

    @Test
    void save_adelantoDeCitaSuelta_quedaParcialSiNoCubreElPrecio() {
        Cita cita = new Cita();
        cita.setId(5L);
        cita.setPrecio(new BigDecimal("100"));
        cita.setMontoPagado(BigDecimal.ZERO);
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));
        CatEstadoPagoCita parcial = new CatEstadoPagoCita();
        parcial.setKey("PARCIAL");
        when(catEstadoPagoCitaRepository.findByKey("PARCIAL")).thenReturn(Optional.of(parcial));

        Pago pago = new Pago();
        Cita ref = new Cita();
        ref.setId(5L);
        pago.setCita(ref);
        pago.setMontoRecibido(new BigDecimal("40"));

        Pago resultado = service.save(pago);

        assertThat(resultado.getMontoAplicado()).isEqualByComparingTo("40");
        assertThat(cita.getMontoPagado()).isEqualByComparingTo("40");
        assertThat(cita.getEstadoPago().getKey()).isEqualTo("PARCIAL");
    }

    @Test
    void delete_revierteElTotalCobradoYRestauraElSaldoPrevio() {
        Tratamiento t = tratamiento(new BigDecimal("50"), 10, new BigDecimal("500"), new BigDecimal("50"));
        when(tratamientoRepository.findById(1L)).thenReturn(Optional.of(t));

        Pago pagoAEliminar = pagoParaTratamiento(1L, new BigDecimal("100"));
        pagoAEliminar.setId(7L);
        pagoAEliminar.setMontoAplicado(new BigDecimal("50"));
        pagoAEliminar.setSaldoPrevio(BigDecimal.ZERO);
        when(repository.findById(7L)).thenReturn(Optional.of(pagoAEliminar));

        boolean eliminado = service.delete(7L);

        assertThat(eliminado).isTrue();
        assertThat(t.getTotalCobrado()).isEqualByComparingTo("450");
        assertThat(t.getSaldoAFavor()).isEqualByComparingTo("0");
    }
}
