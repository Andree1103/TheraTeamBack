package com.therateam.therateam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * Un campo de la ficha.
 *
 * `clave` es la llave con la que el valor se guarda en HistoriaClinica.datos y NO cambia:
 * renombrar la etiqueta que ve el usuario no debe perder lo ya cargado.
 */
@Entity
@Table(name = "hc_campos")
@Data @NoArgsConstructor @AllArgsConstructor
public class HcCampo {
    /** Tipos admitidos — el CHECK de la tabla valida lo mismo del lado de la base. */
    public static final java.util.Set<String> TIPOS = java.util.Set.of(
            "TEXTO", "TEXTO_LARGO", "NUMERO", "FECHA", "BOOLEANO", "SELECT", "MULTISELECT");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seccion_id")
    private HcSeccion seccion;

    private String clave;
    private String etiqueta;
    private String tipo;

    /** Solo para SELECT / MULTISELECT. */
    @Column(columnDefinition = "text[]")
    private String[] opciones;

    private Boolean requerido;
    private String ayuda;
    private Integer orden;

    @PrePersist
    void onCreate() {
        if (orden == null) orden = 0;
        if (requerido == null) requerido = false;
    }
}
