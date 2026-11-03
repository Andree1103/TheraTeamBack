package com.therateam.therateam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dinero ya cobrado por sesiones que todavia no se han dado: lo que habria que devolver si el
 * paciente dejara el tratamiento hoy.
 *
 * Nace de una pregunta que se hacia a mano y salia mal: un paciente paga un paquete de diez
 * sesiones, viene a cinco y pide su dinero. No es (10 - 5) x precio, porque cada cita puede
 * llevar un precio distinto —se reajusta por sesion— y porque parte de lo cobrado puede no estar
 * sobre ninguna cita todavia. Calcularlo cita por cita delante del paciente es lento y es
 * justamente cuando conviene no equivocarse.
 *
 * Se parte en tres porque las tres mitades se cobran de sitios distintos y conviene poder
 * senalar cada una:
 *   - cobradoSinDar: lo que esta aplicado a citas que siguen en pie y sin darse.
 *   - sinRepartir:   lo que entro al paquete y no llego a ninguna cita, porque esas sesiones aun
 *                    no tienen cita creada. Es el que se olvidaba, y olvidarlo hace devolver de
 *                    menos.
 *   - saldoAFavor:   lo que ya estaba a su favor sin pertenecer a ninguna cita.
 */
@Data
@NoArgsConstructor
public class PorDevolverDTO {

    /** Cuantas citas estan cobradas y todavia sin darse. El "contador" propiamente dicho. */
    private long citas;

    /** Lo cobrado que esta aplicado a esas citas. */
    @JsonProperty("cobrado_sin_dar")
    private BigDecimal cobradoSinDar = BigDecimal.ZERO;

    /**
     * Lo cobrado al paquete que no esta sobre ninguna cita.
     *
     * Pasa porque el reparto de un cobro solo alcanza a las sesiones que ya tienen cita (ver
     * PagoService#repartirEntreSesiones: las sesiones sin cita se saltan). Quien pago las diez
     * sesiones de golpe cuando solo habia cuatro agendadas tiene el resto aqui.
     */
    @JsonProperty("sin_repartir")
    private BigDecimal sinRepartir = BigDecimal.ZERO;

    /** Saldo a favor del paciente, el que no esta aplicado a ninguna cita. Solo por paciente. */
    @JsonProperty("saldo_a_favor")
    private BigDecimal saldoAFavor = BigDecimal.ZERO;

    /** La suma de las tres: lo que habria que devolver. */
    private BigDecimal total = BigDecimal.ZERO;

    public PorDevolverDTO(long citas, BigDecimal cobradoSinDar, BigDecimal sinRepartir, BigDecimal saldoAFavor) {
        this.citas = citas;
        this.cobradoSinDar = cobradoSinDar != null ? cobradoSinDar : BigDecimal.ZERO;
        this.sinRepartir = sinRepartir != null ? sinRepartir : BigDecimal.ZERO;
        this.saldoAFavor = saldoAFavor != null ? saldoAFavor : BigDecimal.ZERO;
        this.total = this.cobradoSinDar.add(this.sinRepartir).add(this.saldoAFavor);
    }
}
