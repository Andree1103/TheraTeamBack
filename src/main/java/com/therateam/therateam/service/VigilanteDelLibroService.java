package com.therateam.therateam.service;

import com.therateam.therateam.dto.ReconciliacionDTO;
import com.therateam.therateam.model.Reconciliacion;
import com.therateam.therateam.repository.ReconciliacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pasa el reconciliador cada noche y deja el resultado escrito.
 *
 * El reconciliador ya existia; lo que faltaba era que alguien lo mirase. Un aviso que depende de
 * que una persona se acuerde de ejecutarlo no es un aviso — y el fallo que esto tiene que cazar
 * es justo del tipo que no duele hasta tres semanas despues.
 *
 * LA HORA
 *
 * A las 03:00 de Lima. La app ya pone esa zona por defecto en TherateamApplication, pero aqui
 * se repite en el cron: si alguien quita aquella linea, la tarea seguiria corriendo a la hora
 * correcta en vez de moverse cinco horas sin que nadie lo note. Y a las 3 de la madrugada
 * porque el dia ya esta cerrado y no hay nadie cobrando.
 *
 * LO QUE HACE CUANDO NO CUADRA
 *
 * Lo deja escrito y lo grita en el log con nivel ERROR. No corrige: mientras el libro no sea la
 * fuente, lo guardado manda, y pisar el dato bueno con el derivado de un libro que todavia puede
 * estar incompleto seria peor que el fallo. Avisar es el trabajo; decidir es de una persona.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VigilanteDelLibroService {

    private final ReconciliadorService reconciliador;
    private final ReconciliacionRepository repository;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Lima")
    public void revisarCadaNoche() {
        revisar(true);
    }

    /** La misma revision, lanzada por una persona. Queda marcada como no automatica. */
    @Transactional
    public Reconciliacion revisarAhora() {
        return revisar(false);
    }

    @Transactional
    public Reconciliacion revisar(boolean automatica) {
        ReconciliacionDTO r = reconciliador.revisar();

        Reconciliacion fila = new Reconciliacion();
        fila.setAutomatica(automatica);
        fila.setCuadra(r.isCuadra());
        fila.setDiferencias(r.getDiferencias().size());
        fila.setOrigenes(r.getOrigenes());
        fila.setDestinos(r.getDestinos());
        fila.setDetalle(enTexto(r.getDiferencias()));
        Reconciliacion guardada = repository.save(fila);

        if (r.isCuadra()) {
            log.info("[libro] revision {}: cuadra. Origenes y destinos en S/ {}.",
                    guardada.getId(), r.getOrigenes());
        } else {
            // ERROR y no WARN a proposito: si esto aparece, algo movio dinero sin dejar su
            // asiento. Es exactamente la clase de aviso que no queremos que se pierda entre
            // el ruido.
            log.error("[libro] revision {}: NO CUADRA. {} diferencia(s). Detalle: {}",
                    guardada.getId(), r.getDiferencias().size(), guardada.getDetalle());
        }
        return guardada;
    }

    /** Las ultimas revisiones, de la mas reciente a la mas antigua. */
    @Transactional(readOnly = true)
    public List<Reconciliacion> ultimas(int cuantas) {
        return repository.findAllByOrderByMomentoDesc(PageRequest.of(0, Math.min(cuantas, 200)));
    }

    /** La ultima vez que no cuadro, o null si nunca ha fallado. */
    @Transactional(readOnly = true)
    public Reconciliacion ultimoFallo() {
        List<Reconciliacion> l = repository.ultimosFallos(PageRequest.of(0, 1));
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * El detalle, en texto llano y una linea por diferencia.
     *
     * Va asi y no en JSON porque esto lo lee una persona con prisa cuando algo ha fallado —en
     * la tabla o en el log— y una linea por caso se entiende de un vistazo. Ademas evita atar
     * el servicio a un serializador: Jackson no esta en el classpath de compilacion de este
     * proyecto, y traerlo solo para esto seria una dependencia por una comodidad.
     */
    private static String enTexto(List<ReconciliacionDTO.Diferencia> dif) {
        if (dif.isEmpty()) return null;
        return dif.stream()
                .map(d -> String.format("%s #%d %s — %s: guardado S/ %s, segun el libro S/ %s (dif S/ %s)",
                        d.getTipo(), d.getId(), d.getNombre(), d.getCampo(),
                        d.getGuardado(), d.getSegunElLibro(), d.getDiferencia()))
                .collect(Collectors.joining("\n"));
    }
}
