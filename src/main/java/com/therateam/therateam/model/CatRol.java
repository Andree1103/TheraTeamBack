package com.therateam.therateam.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cat_roles")
@Data @NoArgsConstructor @AllArgsConstructor
public class CatRol {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "key") private String key;
    private String nombre;
    private Boolean activo;

    /** Dato sensible: si es true, los usuarios con este rol ven el celular de los pacientes —
     *  false por defecto, se activa por rol desde Seguridad > Roles (no es un permiso por módulo). */
    @Column(name = "pacientes_ver_telefono")
    private Boolean pacientesVerTelefono;

    /** Si es true, este rol ve los botones de "Exportar Excel" — false por defecto. Es un
     *  control de la interfaz (evita la descarga masiva casual), no una frontera de seguridad:
     *  quien tiene acceso al modulo igual puede leer los datos en pantalla. */
    @Column(name = "puede_exportar")
    private Boolean puedeExportar;

    /** Si es true, este rol puede CORREGIR una atencion (cambiar terapeuta, tipo de terapia,
     *  precio o metodo de pago de una cita ya atendida, que es lo que la edicion normal
     *  prohibe). false por defecto — antes estaba clavado a ADMIN en el codigo. */
    @Column(name = "puede_corregir_atencion")
    private Boolean puedeCorregirAtencion;

    /**
     * Si el rol ve el saldo inicial en la pantalla de Caja.
     *
     * Es el unico numero de esa pantalla que arrastra lo acumulado de dias anteriores, o sea
     * cuanto dinero hay en total. Quien cierra un turno necesita ver lo de SU turno; el
     * acumulado del negocio es otra cosa.
     */
    @Column(name = "puede_ver_saldo_inicial")
    private Boolean puedeVerSaldoInicial;

    /**
     * Si el rol puede cambiar la hora de corte de la caja.
     *
     * Aparte del "editar" del modulo Caja a proposito: ese permiso es para operar el dia
     * —egresos, reabrir un turno— y la hora de corte no es una operacion, es la REGLA que
     * parte el dia en dos. Moverla recalcula que entro en cada turno, incluidos los cerrados.
     */
    @Column(name = "puede_editar_hora_corte")
    private Boolean puedeEditarHoraCorte;

    /**
     * Si el rol puede marcar QUE PASO con una cita: asistio, no asistio o "nadie la marco".
     *
     * Es el permiso que nacio para el visto bueno, con el nombre de lo que hace ahora. El visto
     * bueno se habia pedido para asegurar si el paciente vino, y eso lo dicen mejor las tres
     * respuestas; la columna se renombro en vez de crear otra para conservar quien lo tenia.
     *
     * Va aparte del "crear" del modulo Citas a proposito. Hasta ahora marcar iba con ese mismo
     * permiso, asi que no habia forma de dejar que alguien agendara sin dejarle ademas decir que
     * paso con la cita — que es justo lo que mueve el dinero y las sesiones del paquete.
     */
    @Column(name = "puede_marcar_cita")
    private Boolean puedeMarcarCita;

    /**
     * Un permiso por módulo al que este rol tiene acceso — que exista la fila ya da acceso de
     * lectura/navegación; crear/editar/eliminar son flags independientes (las lecturas de datos
     * de referencia cross-módulo siguen abiertas a cualquier autenticado, ver SecurityConfig).
     */
    @OneToMany(mappedBy = "rol", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RolModuloPermiso> permisos = new ArrayList<>();
}
