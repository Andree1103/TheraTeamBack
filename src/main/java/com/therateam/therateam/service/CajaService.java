package com.therateam.therateam.service;

import com.therateam.therateam.dto.CajaResumenDTO;
import com.therateam.therateam.dto.CerrarCajaRequest;
import com.therateam.therateam.model.CierreCaja;
import com.therateam.therateam.model.Configuracion;
import com.therateam.therateam.repository.CierreCajaRepository;
import com.therateam.therateam.repository.ConfiguracionRepository;
import com.therateam.therateam.repository.PagoRepository;
import com.therateam.therateam.repository.VentaItemRepository;
import com.therateam.therateam.dto.VentaResumenDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cierre de caja por turno: cada día se puede cerrar hasta dos veces — turno 1 (desde el inicio
 * del día hasta la hora de corte configurada) y turno 2 (desde la hora de corte hasta el fin del
 * día). Ingresos del turno (agrupados por método de pago, calculados en vivo desde Pago) +
 * egresos manuales del turno = saldo final, que se arrastra como saldo inicial del turno
 * siguiente (turno 2 del mismo día, o turno 1 del día siguiente). Equivalente a la hoja
 * CUADRARCAJA del sistema anterior en Excel, ahora con dos cortes diarios.
 */
@Service
@RequiredArgsConstructor
public class CajaService {

    private static final String CLAVE_HORA_CORTE = "CAJA_HORA_CORTE_TURNO1";
    private static final LocalTime CORTE_POR_DEFECTO = LocalTime.of(13, 0);

    private final CierreCajaRepository cierreCajaRepository;
    private final PagoRepository pagoRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final VentaItemRepository ventaItemRepository;

    /** Hora de corte entre el turno 1 y el turno 2 — editable desde Configuraciones (clave CAJA_HORA_CORTE_TURNO1). */
    public LocalTime horaCorte() {
        return configuracionRepository.findBySedeIsNullAndClave(CLAVE_HORA_CORTE)
                .map(c -> {
                    try { return LocalTime.parse(c.getValor(), DateTimeFormatter.ofPattern("HH:mm")); }
                    catch (Exception e) { return CORTE_POR_DEFECTO; }
                })
                .orElse(CORTE_POR_DEFECTO);
    }

    @Transactional
    public String actualizarHoraCorte(String horaHHmm) {
        LocalTime.parse(horaHHmm, DateTimeFormatter.ofPattern("HH:mm"));
        Configuracion conf = configuracionRepository.findBySedeIsNullAndClave(CLAVE_HORA_CORTE)
                .orElseGet(Configuracion::new);
        conf.setClave(CLAVE_HORA_CORTE);
        conf.setValor(horaHHmm);
        conf.setDescripcion("Hora de corte entre el turno 1 y turno 2 de caja (formato HH:mm)");
        configuracionRepository.save(conf);
        return horaHHmm;
    }

    @Transactional(readOnly = true)
    public CajaResumenDTO resumenDia(LocalDate fecha, Integer turnoParam) {
        int turno = (turnoParam != null && turnoParam == 2) ? 2 : 1;
        LocalTime corte = horaCorte();
        LocalDateTime inicioRango = turno == 1 ? fecha.atStartOfDay() : fecha.atTime(corte);
        LocalDateTime finRango = turno == 1 ? fecha.atTime(corte) : fecha.plusDays(1).atStartOfDay();

        List<CajaResumenDTO.IngresoMetodo> ingresosPorMetodo = pagoRepository
                .sumMontoPorMetodoEntreFechas(inicioRango, finRango).stream()
                .map(row -> new CajaResumenDTO.IngresoMetodo(
                        (Long) row[0],
                        row[1] != null ? (String) row[1] : "Sin método",
                        (BigDecimal) row[2]))
                .collect(Collectors.toList());

        BigDecimal totalIngresos = ingresosPorMetodo.stream()
                .map(CajaResumenDTO.IngresoMetodo::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // El mismo dinero, cortado por concepto en vez de por método de pago. Son excluyentes
        // entre sí y su suma da totalIngresos — dos vistas del mismo total, no dos totales.
        List<CajaResumenDTO.IngresoConcepto> ingresosPorConcepto = new java.util.ArrayList<>();
        agregarConcepto(ingresosPorConcepto, "TERAPIAS", "Terapias y paquetes",
                pagoRepository.sumTerapiasEntreFechas(inicioRango, finRango));
        agregarConcepto(ingresosPorConcepto, "PRODUCTOS", "Productos",
                pagoRepository.sumProductosEntreFechas(inicioRango, finRango));
        agregarConcepto(ingresosPorConcepto, "OTROS", "Otros cobros",
                pagoRepository.sumOtrosCobrosEntreFechas(inicioRango, finRango));

        // Qué se vendió en el turno: el detalle detrás de la fila "Productos".
        List<VentaResumenDTO> ventasPorProducto = ventaItemRepository.resumenPorProducto(inicioRango, finRango);

        BigDecimal saldoInicial = cierreCajaRepository.findAnteriores(fecha, turno, PageRequest.of(0, 1))
                .stream().findFirst().map(CierreCaja::getSaldoFinal).orElse(BigDecimal.ZERO);

        var cierreExistente = cierreCajaRepository.findByFechaAndTurno(fecha, turno);
        BigDecimal egresos = cierreExistente.map(CierreCaja::getEgresos).orElse(BigDecimal.ZERO);
        String comentario = cierreExistente.map(CierreCaja::getComentario).orElse(null);
        boolean cerrado = cierreExistente.isPresent();
        String cerradoPorNombre = cierreExistente
                .map(c -> c.getCerradoPor() != null ? c.getCerradoPor().getNombre() + " " + c.getCerradoPor().getApellido() : null)
                .orElse(null);

        BigDecimal saldoFinal = saldoInicial.add(totalIngresos).subtract(egresos);

        return new CajaResumenDTO(fecha, turno, corte.format(DateTimeFormatter.ofPattern("HH:mm")),
                saldoInicial, ingresosPorMetodo, ingresosPorConcepto, ventasPorProducto, totalIngresos,
                egresos, comentario, saldoFinal, cerrado, cerradoPorNombre);
    }

    /** Solo se lista el concepto que movió plata: un cero cada día es ruido en el cierre. */
    private void agregarConcepto(List<CajaResumenDTO.IngresoConcepto> destino,
                                  String clave, String nombre, BigDecimal monto) {
        BigDecimal m = monto != null ? monto : BigDecimal.ZERO;
        if (m.compareTo(BigDecimal.ZERO) != 0) {
            destino.add(new CajaResumenDTO.IngresoConcepto(clave, nombre, m));
        }
    }

    @Transactional
    public CajaResumenDTO cerrarDia(CerrarCajaRequest req) {
        LocalDate fecha = req.getFecha();
        int turno = (req.getTurno() != null && req.getTurno() == 2) ? 2 : 1;
        CierreCaja cierre = cierreCajaRepository.findByFechaAndTurno(fecha, turno).orElseGet(CierreCaja::new);

        CajaResumenDTO resumenPrevio = resumenDia(fecha, turno);

        cierre.setFecha(fecha);
        cierre.setTurno(turno);
        cierre.setSaldoInicial(resumenPrevio.getSaldoInicial());
        cierre.setTotalIngresos(resumenPrevio.getTotalIngresos());
        cierre.setEgresos(req.getEgresos() != null ? req.getEgresos() : BigDecimal.ZERO);
        cierre.setComentario(req.getComentario());
        cierre.setSaldoFinal(resumenPrevio.getSaldoInicial()
                .add(resumenPrevio.getTotalIngresos())
                .subtract(cierre.getEgresos()));

        cierreCajaRepository.save(cierre);
        return resumenDia(fecha, turno);
    }

    @Transactional(readOnly = true)
    public List<CierreCaja> historial(LocalDate desde, LocalDate hasta) {
        return cierreCajaRepository.findByFechaBetweenOrderByFechaDescTurnoDesc(desde, hasta);
    }
}
