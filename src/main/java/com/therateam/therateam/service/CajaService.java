package com.therateam.therateam.service;

import com.therateam.therateam.dto.CajaResumenDTO;
import com.therateam.therateam.dto.CerrarCajaRequest;
import com.therateam.therateam.model.CierreCaja;
import com.therateam.therateam.repository.CierreCajaRepository;
import com.therateam.therateam.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cierre de caja diario: ingresos del día (agrupados por método de pago, calculados en vivo
 * desde Pago) + egresos manuales del día = saldo final, que se arrastra como saldo inicial
 * del día siguiente. Equivalente a la hoja CUADRARCAJA del sistema anterior en Excel.
 */
@Service
@RequiredArgsConstructor
public class CajaService {

    private final CierreCajaRepository cierreCajaRepository;
    private final PagoRepository pagoRepository;

    @Transactional(readOnly = true)
    public CajaResumenDTO resumenDia(LocalDate fecha) {
        LocalDateTime inicioDia = fecha.atStartOfDay();
        LocalDateTime finDia = fecha.plusDays(1).atStartOfDay();

        List<CajaResumenDTO.IngresoMetodo> ingresosPorMetodo = pagoRepository
                .sumMontoPorMetodoEntreFechas(inicioDia, finDia).stream()
                .map(row -> new CajaResumenDTO.IngresoMetodo(
                        (Long) row[0],
                        row[1] != null ? (String) row[1] : "Sin método",
                        (BigDecimal) row[2]))
                .collect(Collectors.toList());

        BigDecimal totalIngresos = ingresosPorMetodo.stream()
                .map(CajaResumenDTO.IngresoMetodo::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoInicial = cierreCajaRepository.findFirstByFechaLessThanOrderByFechaDesc(fecha)
                .map(CierreCaja::getSaldoFinal).orElse(BigDecimal.ZERO);

        var cierreExistente = cierreCajaRepository.findByFecha(fecha);
        BigDecimal egresos = cierreExistente.map(CierreCaja::getEgresos).orElse(BigDecimal.ZERO);
        String comentario = cierreExistente.map(CierreCaja::getComentario).orElse(null);
        boolean cerrado = cierreExistente.isPresent();
        String cerradoPorNombre = cierreExistente
                .map(c -> c.getCerradoPor() != null ? c.getCerradoPor().getNombre() + " " + c.getCerradoPor().getApellido() : null)
                .orElse(null);

        BigDecimal saldoFinal = saldoInicial.add(totalIngresos).subtract(egresos);

        return new CajaResumenDTO(fecha, saldoInicial, ingresosPorMetodo, totalIngresos,
                egresos, comentario, saldoFinal, cerrado, cerradoPorNombre);
    }

    @Transactional
    public CajaResumenDTO cerrarDia(CerrarCajaRequest req) {
        LocalDate fecha = req.getFecha();
        CierreCaja cierre = cierreCajaRepository.findByFecha(fecha).orElseGet(CierreCaja::new);

        CajaResumenDTO resumenPrevio = resumenDia(fecha);

        cierre.setFecha(fecha);
        cierre.setSaldoInicial(resumenPrevio.getSaldoInicial());
        cierre.setTotalIngresos(resumenPrevio.getTotalIngresos());
        cierre.setEgresos(req.getEgresos() != null ? req.getEgresos() : BigDecimal.ZERO);
        cierre.setComentario(req.getComentario());
        cierre.setSaldoFinal(resumenPrevio.getSaldoInicial()
                .add(resumenPrevio.getTotalIngresos())
                .subtract(cierre.getEgresos()));

        cierreCajaRepository.save(cierre);
        return resumenDia(fecha);
    }

    @Transactional(readOnly = true)
    public List<CierreCaja> historial(LocalDate desde, LocalDate hasta) {
        return cierreCajaRepository.findByFechaBetweenOrderByFechaDesc(desde, hasta);
    }
}
