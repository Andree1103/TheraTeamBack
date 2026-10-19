package com.therateam.therateam.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Corre algo DESPUÉS de que la operación de fuera haya quedado confirmada, y en su propia
 * transacción.
 *
 * Lo usa el libro para anotar un asiento sin poder estropear el movimiento que lo origina.
 * Suena a detalle técnico y no lo es: es la diferencia entre que un cobro se grabe o se pierda.
 *
 * EL PRIMER INTENTO: un método anotado que se llamaba a sí mismo
 *
 * @Transactional lo aplica un proxy, y un proxy solo ve las llamadas que entran desde fuera. Un
 * método que se invoca a sí mismo se lo salta y la anotación no hace nada. La transacción nueva
 * nunca existió: el asiento se escribía dentro de la del cobro. Cuando fallaba, la excepción se
 * tragaba —eso sí funcionaba— pero la transacción quedaba marcada para deshacer, y al llegar al
 * commit se perdía la operación entera o se quedaba a medias. En producción se vio tal cual: un
 * pago que se borró, devolvió su saldo al paciente y nunca llegó a borrarse.
 *
 * EL SEGUNDO: transacción nueva, pero a destiempo
 *
 * Moverlo a un bean aparte hizo que REQUIRES_NEW funcionara de verdad, y entonces salió el
 * problema que tapaba el fallo anterior: una transacción nueva NO ve lo que la de fuera todavía
 * no ha confirmado. El asiento apunta al pago por clave foránea, y ese pago aún no estaba — así
 * que todos los cobros empezaron a rebotar contra asientos_pago_id_fkey.
 *
 * LO QUE HACE AHORA
 *
 * Espera al commit de fuera y entonces abre la suya. El pago ya existe, la clave foránea lo
 * encuentra, y lo que falle aquí se deshace solo sin tocar nada de lo anterior. Si la operación
 * de fuera se deshace, esto no llega a correr: no había nada que anotar.
 *
 * Sin transacción en curso —una tarea de fondo, un test— corre en el acto, que es lo mismo.
 */
@Service
@Slf4j
public class EnSuPropiaTransaccion {

    private final TransactionTemplate nueva;

    public EnSuPropiaTransaccion(PlatformTransactionManager tm) {
        this.nueva = new TransactionTemplate(tm);
        this.nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** `que` solo sirve para nombrar el fallo en el log si lo hay. */
    public void despuesDeConfirmar(Runnable r, String que) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { correr(r, que); }
            });
        } else {
            correr(r, que);
        }
    }

    private void correr(Runnable r, String que) {
        try {
            nueva.executeWithoutResult(estado -> r.run());
        } catch (RuntimeException e) {
            // Lo de fuera ya está confirmado y no se toca. Lo que se pierde es el asiento, y eso
            // lo destapa el reconciliador — que para eso existe. Va con la excepción entera: el
            // mensaje suelto no decía qué constraint fallaba, y eso costó dar con el fallo.
            log.error("[libro] no se pudo anotar {}: {}", que, e.getMessage(), e);
        }
    }
}
