package com.therateam.therateam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * El resultado de cobrar una cuenta de crédito.
 *
 * Dice lo que se cobró, lo que quedó debiendo y con qué pagos — para que quien cobra vea en el
 * acto si la cuenta quedó en cero o no, sin tener que volver a consultarla.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CobroCreditoDTO {

    private Long cuentaId;
    private String titularNombre;

    private String metodoNombre;
    /** false cuando el método no es dinero en caja — el descuento de planilla, por ejemplo.
     *  Es lo que explica que el cobro no aparezca en el arqueo del día. */
    private boolean entroACaja;

    /** Lo efectivamente aplicado. Puede ser menos de lo pedido si la deuda era menor. */
    private BigDecimal cobrado;
    /** Lo que sigue debiendo la cuenta después de este cobro. */
    private BigDecimal deudaRestante;

    private int lineasSaldadas;
    private int lineasTocadas;

    /** La referencia que comparten los pagos de este cobro, para poder agruparlos en Pagos. */
    private String referencia;
    private List<Long> pagoIds;

    /** Qué quedó de cada línea tocada, en el mismo formato que /lineas. */
    private List<CreditoLineaDTO> detalle;
}
