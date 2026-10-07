#!/usr/bin/env bash
# Bateria de escenarios de dinero contra una copia de produccion.
API=http://localhost:8081
PG="/c/Program Files/PostgreSQL/18/bin/psql.exe"
export PGPASSWORD=a1n2d3r4e5e6 PGCLIENTENCODING=UTF8
q() { "$PG" -t -A -h localhost -U postgres -d BDClinicaMONEY -c "$1"; }
x() { "$PG" -q -h localhost -U postgres -d BDClinicaMONEY -c "$1" >/dev/null; }
TOK=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
      -d '{"email":"jace@whitecode.com.pe","password":"therateam2026"}' | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
post() { curl -s -o /dev/null -w "%{http_code}" -X POST "$API$1" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -d "$2"; }

# Deja el saldo del paciente en un valor exacto. Escribe tambien el movimiento: si solo se
# tocara pacientes.saldo_a_favor, la auditoria (db/auditoria-dinero.sql) marcaria al paciente
# como descuadrado — y seria culpa del arnes, no del sistema.
ponerSaldo() { # $1 paciente, $2 importe
  x "UPDATE pacientes SET saldo_a_favor=$2 WHERE id=$1;
     INSERT INTO saldo_movimientos (paciente_id, monto, saldo_resultante, motivo, fecha)
     VALUES ($1, $2 - COALESCE((SELECT saldo_resultante FROM saldo_movimientos WHERE paciente_id=$1 ORDER BY id DESC LIMIT 1),0),
             $2, 'Ajuste de la bateria de pruebas', now());"
}

OK=0; KO=0
check() { # nombre esperado obtenido
  if [ "$2" = "$3" ]; then OK=$((OK+1)); printf "  ok   %-52s %s\n" "$1" "$3"
  else KO=$((KO+1)); printf "  FALLA %-52s esperaba %s, salio %s\n" "$1" "$2" "$3"; fi
}
nueva_cita() { # precio -> "id paciente"
  q "SELECT c.id||' '||c.paciente_id FROM citas c JOIN cat_estados_cita e ON e.id=c.estado_id JOIN cat_estados_pago_cita ep ON ep.id=c.estado_pago_id WHERE e.key='PROGRAMADA' AND ep.key='SIN_PAGO' AND c.precio=$1 AND c.sesion_id IS NULL AND c.eliminado=false AND NOT EXISTS (SELECT 1 FROM pagos pg WHERE pg.cita_id=c.id) ORDER BY c.id DESC LIMIT 1"
}
saldo() { q "SELECT COALESCE(saldo_a_favor,0)::numeric(12,2) FROM pacientes WHERE id=$1"; }
epago() { q "SELECT ep.key FROM citas c JOIN cat_estados_pago_cita ep ON ep.id=c.estado_pago_id WHERE c.id=$1"; }
pagado(){ q "SELECT c.monto_pagado::numeric(12,2) FROM citas c WHERE c.id=$1"; }

echo "== 1. Adelanto: el saldo sube exactamente lo entregado =="
read C P <<< $(nueva_cita 60); ponerSaldo $P 0
post /api/pagos "{\"paciente\":{\"id\":$P},\"metodo\":{\"id\":1},\"montoRecibido\":100}" >/dev/null
check "saldo tras adelanto de 100" "100.00" "$(saldo $P)"

echo "== 2. Pagar con saldo cuando NO alcanza -> parcial =="
ponerSaldo $P 30
post /api/pagos "{\"paciente\":{\"id\":$P},\"cita\":{\"id\":$C},\"montoRecibido\":0,\"montoAplicado\":60,\"saldoPrevio\":30}" >/dev/null
check "estado de pago de la cita" "PARCIAL" "$(epago $C)"
check "cubierto de la cita"       "30.00"   "$(pagado $C)"
check "saldo restante"            "0.00"    "$(saldo $P)"

echo "== 3. Pagar con saldo cuando SOBRA -> pagada y queda resto =="
read C2 P2 <<< $(nueva_cita 60); ponerSaldo $P2 100
post /api/pagos "{\"paciente\":{\"id\":$P2},\"cita\":{\"id\":$C2},\"montoRecibido\":0,\"montoAplicado\":60,\"saldoPrevio\":100}" >/dev/null
check "estado de pago" "PAGADA" "$(epago $C2)"
check "saldo restante (100-60)" "40.00" "$(saldo $P2)"

echo "== 4. Pagar de mas en efectivo -> el excedente va a saldo =="
read C3 P3 <<< $(nueva_cita 50); ponerSaldo $P3 0
post /api/pagos "{\"paciente\":{\"id\":$P3},\"cita\":{\"id\":$C3},\"metodo\":{\"id\":1},\"montoRecibido\":80,\"montoAplicado\":80}" >/dev/null
check "estado de pago" "PAGADA" "$(epago $C3)"
check "excedente a favor (80-50)" "30.00" "$(saldo $P3)"

echo "== 5. Anular cita pagada EN EFECTIVO -> el dinero vuelve como saldo =="
read C4 P4 <<< $(nueva_cita 50); ponerSaldo $P4 0
post /api/pagos "{\"paciente\":{\"id\":$P4},\"cita\":{\"id\":$C4},\"metodo\":{\"id\":1},\"montoRecibido\":50,\"montoAplicado\":50}" >/dev/null
post "/api/citas/$C4/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
check "saldo tras anular" "50.00" "$(saldo $P4)"

echo "== 6. Anular cita pagada con 'Sin pago' -> NO se crea saldo =="
read C5 P5 <<< $(nueva_cita 50); ponerSaldo $P5 0
post /api/pagos "{\"paciente\":{\"id\":$P5},\"cita\":{\"id\":$C5},\"metodo\":{\"id\":9},\"montoRecibido\":50,\"montoAplicado\":50}" >/dev/null
post "/api/citas/$C5/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
check "saldo tras anular" "0.00" "$(saldo $P5)"

echo "== 7. Cobro adicional (venta) -> no toca el saldo =="
read C6 P6 <<< $(nueva_cita 50); ponerSaldo $P6 25
post /api/pagos "{\"paciente\":{\"id\":$P6},\"cita\":{\"id\":$C6},\"metodo\":{\"id\":1},\"montoRecibido\":20,\"esAdicional\":true,\"concepto\":\"extra\"}" >/dev/null
check "saldo intacto" "25.00" "$(saldo $P6)"
check "la cita sigue sin pagar" "SIN_PAGO" "$(epago $C6)"

echo "== 8. Inasistencia sin devolucion -> DESCONTADA, el dinero se queda =="
read C7 P7 <<< $(nueva_cita 50); ponerSaldo $P7 0
post /api/pagos "{\"paciente\":{\"id\":$P7},\"cita\":{\"id\":$C7},\"metodo\":{\"id\":1},\"montoRecibido\":50,\"montoAplicado\":50}" >/dev/null
post /api/atenciones/inasistencia "{\"citaId\":$C7,\"motivo\":\"no vino\",\"devolver\":false}" >/dev/null
check "estado de pago" "DESCONTADA" "$(epago $C7)"
check "el dinero sigue en la cita" "50.00" "$(pagado $C7)"
check "no se genero saldo" "0.00" "$(saldo $P7)"

echo "== 9. Inasistencia CON devolucion -> vuelve como saldo =="
read C8 P8 <<< $(nueva_cita 50); ponerSaldo $P8 0
post /api/pagos "{\"paciente\":{\"id\":$P8},\"cita\":{\"id\":$C8},\"metodo\":{\"id\":1},\"montoRecibido\":50,\"montoAplicado\":50}" >/dev/null
post /api/atenciones/inasistencia "{\"citaId\":$C8,\"motivo\":\"no vino\",\"devolver\":true}" >/dev/null
check "saldo devuelto" "50.00" "$(saldo $P8)"
check "la cita queda sin pago" "SIN_PAGO" "$(epago $C8)"

echo "== 10. Eliminar un pago -> baja del saldo justo lo que dejo =="
read C9 P9 <<< $(nueva_cita 50); ponerSaldo $P9 0
post /api/pagos "{\"paciente\":{\"id\":$P9},\"metodo\":{\"id\":1},\"montoRecibido\":70}" >/dev/null
PID=$(q "SELECT max(id) FROM pagos WHERE paciente_id=$P9")
check "saldo tras el adelanto" "70.00" "$(saldo $P9)"
curl -s -o /dev/null -X DELETE "$API/api/pagos/$PID" -H "Authorization: Bearer $TOK"
check "saldo tras eliminarlo" "0.00" "$(saldo $P9)"

echo "== 11. Borrar un paquete: se puede si no hay historia, no si la hay =="
read P T TT ET <<< $(q "SELECT c.paciente_id||' '||c.terapeuta_id||' '||c.tipo_terapia_id||' '||(SELECT id FROM cat_estados_tratamiento LIMIT 1) FROM citas c WHERE c.terapeuta_id IS NOT NULL AND c.tipo_terapia_id IS NOT NULL LIMIT 1")
nuevoPaquete() {
  curl -s -X POST "$API/api/tratamientos" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json"     -d "{\"paciente\":{\"id\":$P},\"terapeuta\":{\"id\":$T},\"tipoTerapia\":{\"id\":\"$TT\"},\"nombre\":\"bateria\",\"totalSesiones\":2,\"precioPorSesion\":50,\"estado\":{\"id\":$ET},\"fechaInicio\":\"2026-10-06\"}"     | python -c "import sys,json;print(json.load(sys.stdin)['id'])"
}
conSesiones() { # $1 paquete, $2 estado cita, $3 estado pago, $4 monto pagado
  x "INSERT INTO sesiones (tratamiento_id, numero, estado_id, created_at, updated_at) SELECT $1, g, (SELECT id FROM cat_estados_sesion LIMIT 1), now(), now() FROM generate_series(1,2) g;
     INSERT INTO citas (paciente_id, terapeuta_id, tipo_terapia_id, sesion_id, fecha_inicio, fecha_fin, duracion_minutos, estado_id, modalidad_id, estado_pago_id, precio, monto_pagado, eliminado, created_at, updated_at, recordatorio_enviado)
     SELECT t.paciente_id, t.terapeuta_id, t.tipo_terapia_id, s.id, '2026-11-02 09:00', '2026-11-02 09:40', 40, (SELECT id FROM cat_estados_cita WHERE key='$2'), (SELECT id FROM cat_modalidades LIMIT 1), (SELECT id FROM cat_estados_pago_cita WHERE key='$3'), 50, $4, false, now(), now(), false
     FROM sesiones s JOIN tratamientos t ON t.id=s.tratamiento_id WHERE s.tratamiento_id=$1;"
}
borrar() { curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API/api/tratamientos/$1" -H "Authorization: Bearer $TOK"; }
limpiar() { x "UPDATE sesiones SET cita_activa_id=NULL WHERE tratamiento_id=$1; DELETE FROM citas WHERE sesion_id IN (SELECT id FROM sesiones WHERE tratamiento_id=$1); DELETE FROM sesiones WHERE tratamiento_id=$1; DELETE FROM tratamientos WHERE id=$1;"; }

PQ=$(nuevoPaquete); conSesiones $PQ PROGRAMADA SIN_PAGO 0
check "paquete sin historia se borra" "204" "$(borrar $PQ)"
PQ=$(nuevoPaquete); conSesiones $PQ ASISTIDA PAGADA 50
check "paquete con sesion atendida se niega" "400" "$(borrar $PQ)"; limpiar $PQ
PQ=$(nuevoPaquete); conSesiones $PQ PROGRAMADA PARCIAL 30
check "paquete con dinero cobrado se niega" "400" "$(borrar $PQ)"; limpiar $PQ

# Los dos de abajo reproducen lo que manda el FRONT, no lo que seria comodo mandar. El fallo que
# reporto el cliente no estaba en el motor del dinero —que siempre supo gastar el saldo— sino en
# que estas dos pantallas cobraban el importe completo sin mirarlo. Asi que lo que se comprueba
# aqui es la peticion tal cual sale de ellas.

echo "== 12. Pagar sesiones marcadas: el saldo cubre las que alcanza, el resto por su metodo =="
# Tres sesiones de 50 y un saldo de 120: cubre dos enteras (100) y la tercera se cobra aparte.
# Nunca media sesion: pagar 20 de una dejaria una cita PARCIAL que nadie pidio.
read P T TT ET <<< $(q "SELECT c.paciente_id||' '||c.terapeuta_id||' '||c.tipo_terapia_id||' '||(SELECT id FROM cat_estados_tratamiento LIMIT 1) FROM citas c WHERE c.terapeuta_id IS NOT NULL AND c.tipo_terapia_id IS NOT NULL LIMIT 1")
PQ2=$(curl -s -X POST "$API/api/tratamientos" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
      -d "{\"paciente\":{\"id\":$P},\"terapeuta\":{\"id\":$T},\"tipoTerapia\":{\"id\":\"$TT\"},\"nombre\":\"bateria sesiones\",\"totalSesiones\":3,\"precioPorSesion\":50,\"estado\":{\"id\":$ET},\"fechaInicio\":\"2026-10-06\"}" \
      | python -c "import sys,json;print(json.load(sys.stdin)['id'])")
x "INSERT INTO sesiones (tratamiento_id, numero, estado_id, created_at, updated_at) SELECT $PQ2, g, (SELECT id FROM cat_estados_sesion LIMIT 1), now(), now() FROM generate_series(1,3) g;
   INSERT INTO citas (paciente_id, terapeuta_id, tipo_terapia_id, sesion_id, fecha_inicio, fecha_fin, duracion_minutos, estado_id, modalidad_id, estado_pago_id, precio, monto_pagado, eliminado, created_at, updated_at, recordatorio_enviado)
   SELECT t.paciente_id, t.terapeuta_id, t.tipo_terapia_id, s.id, '2026-11-03 09:00', '2026-11-03 09:40', 40, (SELECT id FROM cat_estados_cita WHERE key='PROGRAMADA'), (SELECT id FROM cat_modalidades LIMIT 1), (SELECT id FROM cat_estados_pago_cita WHERE key='SIN_PAGO'), 50, 0, false, now(), now(), false
   FROM sesiones s JOIN tratamientos t ON t.id=s.tratamiento_id WHERE s.tratamiento_id=$PQ2;
   UPDATE sesiones s SET cita_activa_id = (SELECT c.id FROM citas c WHERE c.sesion_id=s.id) WHERE s.tratamiento_id=$PQ2;"
read S1 S2 S3 <<< $(q "SELECT string_agg(c.id::text,' ' ORDER BY s.numero) FROM sesiones s JOIN citas c ON c.sesion_id=s.id WHERE s.tratamiento_id=$PQ2")
ponerSaldo $P 120
# Cada pago va dirigido a UNA cita, igual que el front: montoRecibido 0 y sin metodo cuando lo
# cubre el saldo. Si el motor cobrara contra la deuda del paquete en vez de la de la sesion, el
# primer pago se tragaria los 120 y la cita, topada en 50, dejaria 70 en el aire.
post /api/pagos "{\"paciente\":{\"id\":$P},\"tratamiento\":{\"id\":$PQ2},\"cita\":{\"id\":$S1},\"montoRecibido\":0}" >/dev/null
post /api/pagos "{\"paciente\":{\"id\":$P},\"tratamiento\":{\"id\":$PQ2},\"cita\":{\"id\":$S2},\"montoRecibido\":0}" >/dev/null
check "saldo tras cubrir 2 de 3 (120-100)" "20.00" "$(saldo $P)"
check "la 1ra sesion queda pagada" "PAGADA" "$(epago $S1)"
check "la 2da sesion queda pagada" "PAGADA" "$(epago $S2)"
check "la 3ra sigue sin pagar"     "SIN_PAGO" "$(epago $S3)"
# La tercera en Yape: entra dinero de verdad, asi que lleva metodo.
post /api/pagos "{\"paciente\":{\"id\":$P},\"tratamiento\":{\"id\":$PQ2},\"cita\":{\"id\":$S3},\"metodo\":{\"id\":1},\"montoRecibido\":50}" >/dev/null
check "cobrado del paquete (3x50)" "150.00" "$(q "SELECT total_cobrado::numeric(12,2) FROM tratamientos WHERE id=$PQ2")"
check "nada se quedo en el aire"   "150.00" "$(q "SELECT COALESCE(sum(c.monto_pagado),0)::numeric(12,2) FROM sesiones s JOIN citas c ON c.sesion_id=s.id WHERE s.tratamiento_id=$PQ2")"
check "el saldo no se toco al cobrar la tercera" "20.00" "$(saldo $P)"
check "solo 1 de los 3 pagos trajo dinero" "1" "$(q "SELECT count(*) FROM pagos WHERE tratamiento_id=$PQ2 AND trajo_dinero")"

echo "== 13. Pago inicial del paquete: descuenta del saldo y solo pide la diferencia =="
ponerSaldo $P 80
PQ3=$(curl -s -X POST "$API/api/tratamientos" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
      -d "{\"paciente\":{\"id\":$P},\"terapeuta\":{\"id\":$T},\"tipoTerapia\":{\"id\":\"$TT\"},\"nombre\":\"bateria inicial\",\"totalSesiones\":2,\"precioPorSesion\":50,\"estado\":{\"id\":$ET},\"fechaInicio\":\"2026-10-06\"}" \
      | python -c "import sys,json;print(json.load(sys.stdin)['id'])")
# Pago inicial de 100 con 80 de saldo: el front manda 20 en efectivo, no 100.
post /api/pagos "{\"paciente\":{\"id\":$P},\"tratamiento\":{\"id\":$PQ3},\"metodo\":{\"id\":1},\"montoRecibido\":20,\"montoAplicado\":100,\"saldoPrevio\":80}" >/dev/null
check "cobrado del paquete" "100.00" "$(q "SELECT total_cobrado::numeric(12,2) FROM tratamientos WHERE id=$PQ3")"
check "saldo consumido entero"   "0.00" "$(saldo $P)"
check "en caja solo entraron 20" "20.00" "$(q "SELECT monto_recibido::numeric(12,2) FROM pagos WHERE tratamiento_id=$PQ3")"

echo "== 14. El saldo solo se gasta si se pide =="
# El caso de JOSE CARLOS: un cobro de 45 contra un paquete de 235 se llevaba por delante los
# 190 que el paciente tenia a favor y dejaba todo pagado. Ahora el cobro dice cuanto saldo usa.
read P T TT ET <<< $(q "SELECT c.paciente_id||' '||c.terapeuta_id||' '||c.tipo_terapia_id||' '||(SELECT id FROM cat_estados_tratamiento LIMIT 1) FROM citas c WHERE c.terapeuta_id IS NOT NULL AND c.tipo_terapia_id IS NOT NULL LIMIT 1")
nuevoPaq() { # $1 sesiones, $2 precio
  curl -s -X POST "$API/api/tratamientos" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
    -d "{\"paciente\":{\"id\":$P},\"terapeuta\":{\"id\":$T},\"tipoTerapia\":{\"id\":\"$TT\"},\"nombre\":\"bateria saldo\",\"totalSesiones\":$1,\"precioPorSesion\":$2,\"estado\":{\"id\":$ET},\"fechaInicio\":\"2026-10-07\"}" \
    | python -c "import sys,json;print(json.load(sys.stdin)['id'])"
}
PA=$(nuevoPaq 5 47); ponerSaldo $P 190
post /api/pagos "{\"paciente\":{\"id\":$P},\"tratamiento\":{\"id\":$PA},\"metodo\":{\"id\":1},\"montoRecibido\":45,\"saldoAAplicar\":0}" >/dev/null
check "cobrado del paquete (solo los 45)" "45.00"  "$(q "SELECT total_cobrado::numeric(12,2) FROM tratamientos WHERE id=$PA")"
check "su saldo sigue intacto"            "190.00" "$(saldo $P)"
# Y pidiendolo, se usa: 190 de saldo sobre 235 dejan 45 de deuda, que es lo que el negocio queria.
PB=$(nuevoPaq 5 47)
post /api/pagos "{\"paciente\":{\"id\":$P},\"tratamiento\":{\"id\":$PB},\"montoRecibido\":0,\"saldoAAplicar\":190}" >/dev/null
check "cobrado con su saldo"   "190.00" "$(q "SELECT total_cobrado::numeric(12,2) FROM tratamientos WHERE id=$PB")"
check "deuda que queda (235-190)" "45.00" "$(q "SELECT (235 - total_cobrado)::numeric(12,2) FROM tratamientos WHERE id=$PB")"
check "saldo agotado"          "0.00"   "$(saldo $P)"

echo "== 15. Anular una cita pagada CON SALDO: el saldo vuelve =="
# Antes no volvia: el filtro preguntaba "¿entro efectivo?" y un pago con saldo responde que no,
# asi que al paciente se le comia el saldo al cobrar y no se le devolvia al anular.
read C8 P8 <<< $(nueva_cita 50); ponerSaldo $P8 80
post /api/pagos "{\"paciente\":{\"id\":$P8},\"cita\":{\"id\":$C8},\"montoRecibido\":0,\"montoAplicado\":50,\"saldoAAplicar\":50}" >/dev/null
check "saldo tras cobrar con saldo (80-50)" "30.00" "$(saldo $P8)"
check "la cita queda pagada" "PAGADA" "$(epago $C8)"
post "/api/citas/$C8/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
check "saldo tras anular (vuelven los 50)" "80.00" "$(saldo $P8)"
# Y lo de "Sin pago" sigue sin inventar credito.
read C9b P9b <<< $(nueva_cita 50); ponerSaldo $P9b 0
post /api/pagos "{\"paciente\":{\"id\":$P9b},\"cita\":{\"id\":$C9b},\"metodo\":{\"id\":9},\"montoRecibido\":50}" >/dev/null
post "/api/citas/$C9b/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
check "'Sin pago' sigue sin generar saldo" "0.00" "$(saldo $P9b)"

echo "== 16. Devolver dinero: ni doble, ni dentro de la caja del dia =="
# Pago mixto: 20 en efectivo + 30 de su saldo. Al devolver, por el cajon salen 20 — los 30 ya
# le vuelven como saldo. Antes se grababa una devolucion por los 50 enteros Y se le restituia
# el saldo: contaba dos veces.
read CA PA2 <<< $(nueva_cita 50); ponerSaldo $PA2 30
post /api/pagos "{\"paciente\":{\"id\":$PA2},\"cita\":{\"id\":$CA},\"metodo\":{\"id\":1},\"montoRecibido\":20,\"saldoAAplicar\":30}" >/dev/null
check "la cita queda pagada" "PAGADA" "$(epago $CA)"
check "saldo consumido"      "0.00"   "$(saldo $PA2)"
post "/api/citas/$CA/anular?devolucion=DINERO&motivo=prueba" '{}' >/dev/null
check "le vuelven sus 30 de saldo"        "30.00" "$(saldo $PA2)"
check "por el cajon salen solo los 20"    "20.00" "$(q "SELECT COALESCE(sum(monto_recibido),0)::numeric(12,2) FROM pagos WHERE cita_id=$CA AND es_devolucion")"
check "grabada como Devolución"           "Devolución" "$(q "SELECT m.nombre FROM pagos p JOIN cat_metodos_pago m ON m.id=p.metodo_id WHERE p.cita_id=$CA AND p.es_devolucion")"
check "dice por donde salio"              "1" "$(q "SELECT count(*) FROM pagos WHERE cita_id=$CA AND es_devolucion AND notas ILIKE '%Efectivo%'")"
check "fuera del arqueo del dia"          "false" "$(q "SELECT trajo_dinero::text FROM pagos WHERE cita_id=$CA AND es_devolucion")"

echo
echo "RESULTADO: $OK correctos, $KO fallos"
