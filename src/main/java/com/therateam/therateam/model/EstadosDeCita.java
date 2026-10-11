package com.therateam.therateam.model;

import java.util.List;

/**
 * Las claves de estado de cita, agrupadas por la pregunta que se les hace.
 *
 * Existen aqui y no repartidas por cada servicio porque la lista se habia copiado a trozos y los
 * trozos no coincidian: al meter citas sueltas en un paquete se excluian cuatro estados cuando el
 * catalogo tiene ocho que significan "esta cita ya no va". Las canceladas por paciente o por
 * clinica se colaban, y con ellas las dos variantes viejas de inasistencia.
 */
public final class EstadosDeCita {

    private EstadosDeCita() {}

    /**
     * "Esta cita ya no va."
     *
     * Hoy solo se escribe ANULADA —el porque se guarda como texto en la cita, no como estados
     * distintos— pero las dos viejas siguen en el catalogo desactivadas, y bastaria una fila sin
     * migrar para que una cita anulada volviera a contar como activa.
     */
    public static final List<String> CANCELADAS =
            List.of("ANULADA", "CANCELADA_PACIENTE", "CANCELADA_CLINICA");

    /**
     * El paciente no vino.
     *
     * Las dos con nombre largo son de antes de que la inasistencia se registrara con su propio
     * flujo; quedan desactivadas en el catalogo y se listan por el mismo motivo que arriba.
     */
    public static final List<String> INASISTENCIAS =
            List.of("NO_ASISTIO", "INASISTENCIA", "INASISTENCIA SIN");

    /**
     * Estados en los que ya no se le puede COBRAR nada nuevo a la cita.
     *
     * Ojo con lo que NO esta aqui: ASISTIDA. Una sesion que se dio y no se pago es una deuda
     * legitima y tiene que poder cobrarse — sacarla de la lista de cobro seria perder plata.
     *
     * Suma a canceladas e inasistencias la REPROGRAMADA: su sesion y su dinero se fueron a la
     * cita nueva, y la vieja solo queda como rastro. Cobrarla seria cobrar dos veces.
     */
    public static final List<String> NO_SE_COBRAN = java.util.stream.Stream
            .of(CANCELADAS, INASISTENCIAS, List.of("REPROGRAMADA"))
            .flatMap(List::stream).toList();

    /**
     * Estados en los que la cita ya no es una sesion POR DAR, asi que no puede entrar en un
     * paquete.
     *
     * Es la de arriba mas ASISTIDA, y esa diferencia es el motivo de que sean dos listas y no
     * una: una sesion ya dada se puede cobrar pero no se puede meter en un paquete nuevo —
     * ocuparia una plaza de algo que todavia esta por ocurrir y descuadraria el contador de
     * atendidas del paquete desde el primer dia.
     */
    public static final List<String> YA_NO_ES_UNA_SESION_POR_DAR = java.util.stream.Stream
            .of(NO_SE_COBRAN, List.of("ASISTIDA"))
            .flatMap(List::stream).toList();

    public static boolean yaNoEsUnaSesionPorDar(String estadoKey) {
        return estadoKey != null && YA_NO_ES_UNA_SESION_POR_DAR.contains(estadoKey.trim());
    }

    public static boolean noSeCobra(String estadoKey) {
        return estadoKey != null && NO_SE_COBRAN.contains(estadoKey.trim());
    }
}
