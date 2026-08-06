package com.therateam.therateam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class TherateamApplication {

	public static void main(String[] args) {
		/*
		 * Toda la app (horarios de terapeutas, "no crear citas en el pasado", createdAt, etc.)
		 * asume hora de Perú. En local coincide con el reloj del sistema, pero en Railway el
		 * contenedor corre en UTC por defecto — sin esto, LocalDateTime.now() del servidor queda
		 * ~5h adelantado respecto al cliente, y cualquier cita de la mañana/temprano en la tarde
		 * se rechaza como "fecha pasada" aunque en Perú todavía no haya llegado esa hora.
		 */
		TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
		SpringApplication.run(TherateamApplication.class, args);
	}

}
