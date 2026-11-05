#!/usr/bin/env bash
# Bateria de escenarios de dinero contra una copia de produccion.
# El puerto se cambia sin tocar el archivo: API=http://localhost:8080 bash db/pruebas-dinero.sh
API="${API:-http://localhost:8081}"
PG="/c/Program Files/PostgreSQL/18/bin/psql.exe"
export PGPASSWORD=a1n2d3r4e5e6 PGCLIENTENCODING=UTF8
# Contra que copia se corre. Se cambia sin tocar el archivo:
#   BD=BDClinicaLOQUESEA bash db/pruebas-dinero.sh
# Nunca contra produccion: la bateria cobra, anula y borra de verdad.
BD="${BD:-BDClinicaHOY7}"
q() { "$PG" -t -A -h localhost -U postgres -d "$BD" -c "$1"; }
x() { "$PG" -q -h localhost -U postgres -d "$BD" -c "$1" >/dev/null; }
TOK=$(curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
      -d '{"email":"jace@whitecode.com.pe","password":"therateam2026"}' | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
post() { curl -s -o /dev/null -w "%{http_code}" -X POST "$API$1" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -d "$2"; }

# Deja el saldo del paciente en un valor exacto. Escribe tambien el movimiento: si solo se
# tocara pacientes.saldo_a_favor, la auditoria (db/auditoria-dinero.sql) marcaria al paciente
# como descuadrado — y seria culpa del arnes, no del sistema.
ponerSaldo() { # $1 paciente, $2 importe
  # Tres escrituras, no una. El arnes tiene que dejar el mismo rastro que dejaria el sistema:
  # el saldo, su movimiento y su asiento en el libro. Si se salta alguno, el descuadre que
  # aparece luego es culpa de la prueba y no del codigo — y perseguir eso cuesta una tarde.
  # Ya paso con saldo_movimientos; el asiento se olvido al montar el libro y el reconciliador
  # canto 11 pacientes que estaban perfectamente.
  x "UPDATE pacientes SET saldo_a_favor=$2 WHERE id=$1;
     INSERT INTO saldo_movimientos (paciente_id, monto, saldo_resultante, motivo, fecha)
     VALUES ($1, $2 - COALESCE((SELECT saldo_resultante FROM saldo_movimientos WHERE paciente_id=$1 ORDER BY id DESC LIMIT 1),0),
             $2, 'Ajuste de la bateria de pruebas', now());"
  # El asiento: la diferencia contra lo que el libro ya le reconoce. Positiva se le reconoce
  # credito, negativa se le retira; la contrapartida es constancia porque no pasa por caja.
  x "WITH antes AS (
       SELECT COALESCE(SUM(CASE WHEN l.concepto='CREDITO_GENERA' THEN l.importe
                                WHEN l.concepto='CREDITO_USA'    THEN -l.importe ELSE 0 END),0) AS libro
         FROM asientos a JOIN asiento_lineas l ON l.asiento_id=a.id WHERE a.paciente_id=$1),
     d AS (SELECT ($2 - libro)::numeric(12,2) AS v FROM antes),
     nuevo AS (
       INSERT INTO asientos (tipo, fecha, paciente_id, nota)
       SELECT 'AJUSTE', now(), $1, 'Ajuste de la bateria de pruebas' FROM d WHERE abs(v) > 0.005
       RETURNING id)
     INSERT INTO asiento_lineas (asiento_id, concepto, importe)
     SELECT nuevo.id, c.concepto, c.importe FROM nuevo, d
     CROSS JOIN LATERAL (VALUES
        (CASE WHEN d.v > 0 THEN 'CONSTANCIA'     ELSE 'CREDITO_USA'       END, abs(d.v)),
        (CASE WHEN d.v > 0 THEN 'CREDITO_GENERA' ELSE 'CONSTANCIA_LIBERA' END, abs(d.v))
     ) AS c(concepto, importe);"
}

OK=0; KO=0
check() { # nombre esperado obtenido
  if [ "$2" = "$3" ]; then OK=$((OK+1)); printf "  ok   %-52s %s\n" "$1" "$3"
  else KO=$((KO+1)); printf "  FALLA %-52s esperaba %s, salio %s\n" "$1" "$2" "$3"; fi
}
# Una cita limpia con ese precio, para gastarla en un caso.
#
# CREA la suya en vez de buscar una existente. Antes buscaba, y como cada pasada deja la cita
# pagada o anulada, la bateria se quedaba sin candidatas y empezaba a fallar en masa: 30 fallos
# que no eran del codigo sino de haberla corrido demasiadas veces. Pasa tres veces antes de que
# me diera por arreglarlo.
#
# Al crearlas, correrla dos veces seguidas da el mismo resultado, que es lo minimo que se le
# pide a una bateria.
nueva_cita() { # precio -> "id paciente"
  x "INSERT INTO citas (paciente_id, terapeuta_id, tipo_terapia_id, fecha_inicio, fecha_fin,
                        duracion_minutos, estado_id, modalidad_id, estado_pago_id, precio,
                        monto_pagado, eliminado, created_at, updated_at, recordatorio_enviado)
     SELECT c.paciente_id, c.terapeuta_id, c.tipo_terapia_id,
            now() + interval '30 day', now() + interval '30 day' + interval '40 minute', 40,
            (SELECT id FROM cat_estados_cita WHERE key='PROGRAMADA'),
            c.modalidad_id,
            (SELECT id FROM cat_estados_pago_cita WHERE key='SIN_PAGO'),
            $1, 0, false, now(), now(), false
       FROM citas c
      WHERE c.terapeuta_id IS NOT NULL AND c.tipo_terapia_id IS NOT NULL AND c.modalidad_id IS NOT NULL
      ORDER BY c.id DESC LIMIT 1"
  q "SELECT c.id||' '||c.paciente_id FROM citas c ORDER BY c.id DESC LIMIT 1"
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
# A_FAVOR, no SIN_PAGO: la cita deja dicho que su dinero se le quedo al paciente a favor
# (ver el caso 17). Esta expectativa se quedo vieja cuando se separaron las dos marcas.
check "la cita queda marcada a favor" "A_FAVOR" "$(epago $C8)"

echo "== 10. Eliminar un pago -> baja del saldo justo lo que dejo =="
read C9 P9 <<< $(nueva_cita 50); ponerSaldo $P9 0
post /api/pagos "{\"paciente\":{\"id\":$P9},\"metodo\":{\"id\":1},\"montoRecibido\":70}" >/dev/null
PID=$(q "SELECT max(id) FROM pagos WHERE paciente_id=$P9")
check "saldo tras el adelanto" "70.00" "$(saldo $P9)"
# El motivo es obligatorio desde que borrar un pago exige decir por que: sin el, el DELETE
# se rechaza y la bateria veia el saldo intacto como si el sistema no lo hubiera revertido.
curl -s -o /dev/null -X DELETE "$API/api/pagos/$PID?motivo=bateria+de+pruebas" -H "Authorization: Bearer $TOK"
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
# Y lo de "Sin pago" sigue sin inventar credito. El id del medio se busca: no esta en todas
# las copias, y mandarlo a pelo hacia que el cobro se rechazara por clave foranea — el check
# veia el saldo en 0 y pasaba sin haber probado nada.
MSINPAGO=$(q "SELECT id FROM cat_metodos_pago WHERE key ILIKE '%SIN%PAGO%' OR nombre ILIKE '%sin pago%' LIMIT 1")
if [ -n "$MSINPAGO" ]; then
  read C9b P9b <<< $(nueva_cita 50); ponerSaldo $P9b 0
  post /api/pagos "{\"paciente\":{\"id\":$P9b},\"cita\":{\"id\":$C9b},\"metodo\":{\"id\":$MSINPAGO},\"montoRecibido\":50}" >/dev/null
  post "/api/citas/$C9b/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
  check "'Sin pago' sigue sin generar saldo" "0.00" "$(saldo $P9b)"
else
  echo "  (salta: esta copia no tiene el medio 'Sin pago')"
fi

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

echo "== 17. Cobro mixto anulado QUEDANDO a favor: todo pasa a saldo, la caja no se mueve =="
# El espejo del caso 16. Mismo cobro —20 por Yape y 30 de su saldo— pero sin sacar dinero: los
# 50 enteros se le quedan a favor. Los 30 vuelven de donde salieron y los 20 que ya habia
# entregado se le quedan como credito en vez de devolverselos en mano.
read CB PB <<< $(nueva_cita 50); ponerSaldo $PB 30
post /api/pagos "{\"paciente\":{\"id\":$PB},\"cita\":{\"id\":$CB},\"metodo\":{\"id\":1},\"montoRecibido\":20,\"saldoAAplicar\":30}" >/dev/null
check "la cita queda pagada" "PAGADA" "$(epago $CB)"
check "saldo consumido"      "0.00"   "$(saldo $PB)"
post "/api/citas/$CB/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
check "los 50 enteros quedan a favor" "50.00" "$(saldo $PB)"
check "no sale nada del cajon"        "0"     "$(q "SELECT count(*) FROM pagos WHERE cita_id=$CB AND es_devolucion")"
# No SIN_PAGO: desde que la cita dice QUE se hizo con su dinero, "a favor" tiene su propia
# marca. SIN_PAGO es para cuando no hubo credito que reconocer (un cobro que no movio dinero).
check "la cita queda marcada a favor" "A_FAVOR" "$(epago $CB)"

echo "== 18. Borrar un cobro adicional NO le regala saldo al paciente =="
# El cobro adicional no toca el saldo. Pero grababa saldo_generado = 0 con saldo_previo = lo que
# el paciente tuviera, y como el consumo de credito se lee restando los dos, la fila decia que
# se habia comido su saldo entero. Al borrarla, revertir() deshacia ese consumo inventado: con
# S/ 25 a favor, borrar una venta de S/ 20 dejaba al paciente con S/ 50.
read CC PC <<< $(nueva_cita 50); ponerSaldo $PC 25
post /api/pagos "{\"paciente\":{\"id\":$PC},\"cita\":{\"id\":$CC},\"metodo\":{\"id\":1},\"montoRecibido\":20,\"esAdicional\":true,\"concepto\":\"venta\"}" >/dev/null
PIDA=$(q "SELECT max(id) FROM pagos WHERE paciente_id=$PC AND es_adicional")
check "saldo intacto tras la venta" "25.00" "$(saldo $PC)"
check "la fila no finge consumo"    "0.00"  "$(q "SELECT (COALESCE(saldo_previo,0)-COALESCE(saldo_generado,0))::numeric(12,2) FROM pagos WHERE id=$PIDA")"
curl -s -o /dev/null -X DELETE "$API/api/pagos/$PIDA" -H "Authorization: Bearer $TOK"
check "saldo intacto tras borrarla" "25.00" "$(saldo $PC)"

echo "== 19. Anular una sesion de un paquete: devolver el dinero no deja deuda =="
# El caso de LIAM: paquete de 2 sesiones pagado entero. Se anula una cita y se le devuelve su
# dinero. La deuda se lee en todas las pantallas como "lo que vale el paquete menos lo cobrado";
# devolver bajaba lo cobrado y dejaba el valor intacto, asi que el paquete pasaba a reclamar
# justo lo que se acababa de devolver. Ahora la sesion sale del paquete con su dinero.
read P T TT ET <<< $(q "SELECT c.paciente_id||' '||c.terapeuta_id||' '||c.tipo_terapia_id||' '||(SELECT id FROM cat_estados_tratamiento WHERE key='ACTIVO') FROM citas c WHERE c.terapeuta_id IS NOT NULL AND c.tipo_terapia_id IS NOT NULL ORDER BY c.id DESC LIMIT 1")
paq() { # $1 sesiones, $2 precio -> id
  curl -s -X POST "$API/api/tratamientos" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
    -d "{\"paciente\":{\"id\":$P},\"terapeuta\":{\"id\":$T},\"tipoTerapia\":{\"id\":\"$TT\"},\"nombre\":\"bateria anular\",\"totalSesiones\":$1,\"precioPorSesion\":$2,\"estado\":{\"id\":$ET},\"fechaInicio\":\"2026-11-04\"}" \
    | python -c "import sys,json;print(json.load(sys.stdin)['id'])"
}
# Las citas del paquete tal como quedan al agendarlas: cada sesion con su cita y su cita_activa.
citasDe() { # $1 paquete, $2 cuantas, $3 precio -> ids por numero de sesion
  x "INSERT INTO sesiones (tratamiento_id, numero, estado_id, created_at, updated_at)
     SELECT $1, g, (SELECT id FROM cat_estados_sesion LIMIT 1), now(), now() FROM generate_series(1,$2) g;
     INSERT INTO citas (paciente_id, terapeuta_id, tipo_terapia_id, sesion_id, fecha_inicio, fecha_fin,
                        duracion_minutos, estado_id, modalidad_id, estado_pago_id, precio, monto_pagado,
                        eliminado, created_at, updated_at, recordatorio_enviado)
     SELECT t.paciente_id, t.terapeuta_id, t.tipo_terapia_id, s.id,
            now() + interval '20 day' + (s.numero * interval '1 hour'),
            now() + interval '20 day' + (s.numero * interval '1 hour') + interval '40 minute', 40,
            (SELECT id FROM cat_estados_cita WHERE key='PROGRAMADA'), (SELECT id FROM cat_modalidades LIMIT 1),
            (SELECT id FROM cat_estados_pago_cita WHERE key='SIN_PAGO'), $3, 0, false, now(), now(), false
       FROM sesiones s JOIN tratamientos t ON t.id=s.tratamiento_id WHERE s.tratamiento_id=$1;
     UPDATE sesiones s SET cita_activa_id=(SELECT c.id FROM citas c WHERE c.sesion_id=s.id) WHERE s.tratamiento_id=$1;"
  q "SELECT string_agg(c.id::text,' ' ORDER BY s.numero) FROM sesiones s JOIN citas c ON c.sesion_id=s.id WHERE s.tratamiento_id=$1"
}
deudaPaq()    { q "SELECT (monto_total - total_cobrado)::numeric(12,2) FROM tratamientos WHERE id=$1"; }
sesionesPaq() { q "SELECT total_sesiones FROM tratamientos WHERE id=$1"; }
cobradoPaq()  { q "SELECT total_cobrado::numeric(12,2) FROM tratamientos WHERE id=$1"; }
pagarSesion() { # $1 paquete, $2 cita, $3 importe
  post /api/pagos "{\"paciente\":{\"id\":$P},\"tratamiento\":{\"id\":$1},\"cita\":{\"id\":$2},\"metodo\":{\"id\":1},\"montoRecibido\":$3}" >/dev/null
}

PQ=$(paq 2 50); read K1 K2 <<< $(citasDe $PQ 2 50); ponerSaldo $P 0
pagarSesion $PQ $K1 50; pagarSesion $PQ $K2 50
check "paquete pagado, sin deuda" "0.00" "$(deudaPaq $PQ)"
post "/api/citas/$K1/anular?devolucion=DINERO&motivo=prueba" '{}' >/dev/null
check "el paquete se queda en 1 sesion"   "1"        "$(sesionesPaq $PQ)"
check "y sin deuda tras devolver"         "0.00"     "$(deudaPaq $PQ)"
check "cobrado tras devolver"             "50.00"    "$(cobradoPaq $PQ)"
check "salieron 50 por el cajon"          "50.00"    "$(q "SELECT COALESCE(sum(monto_recibido),0)::numeric(12,2) FROM pagos WHERE tratamiento_id=$PQ AND es_devolucion")"
check "la cita dice que se devolvio"      "DEVUELTO" "$(epago $K1)"
check "al paciente no se le regalo saldo" "0.00"     "$(saldo $P)"

echo "== 20. La misma anulacion dejando el dinero A FAVOR: credito si, deuda no =="
# Antes salian las dos cosas a la vez en la ficha: S/ 50 a favor y S/ 50 de deuda por la misma
# sesion. El credito es de verdad; la deuda era el reembolso contado al reves.
PQB=$(paq 2 50); read L1 L2 <<< $(citasDe $PQB 2 50); ponerSaldo $P 0
pagarSesion $PQB $L1 50; pagarSesion $PQB $L2 50
post "/api/citas/$L1/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
check "los 50 le quedan a favor" "50.00" "$(saldo $P)"
check "y el paquete sin deuda"   "0.00"  "$(deudaPaq $PQB)"
check "nada salio del cajon"     "0"     "$(q "SELECT count(*) FROM pagos WHERE tratamiento_id=$PQB AND es_devolucion")"

echo "== 21. Cobro PARCIAL anulado: queda por cobrar la sesion que sigue en pie, no las dos =="
# El paquete baja de una sesion en una, no por el importe devuelto: lo que cambia es que esa
# sesion no se va a dar. 2 de 50 con 30 cobrados sobre la primera; al anularla y devolver los 30,
# lo pendiente son los 50 de la sesion que le queda. Antes decia 100.
PQC=$(paq 2 50); read M1 M2 <<< $(citasDe $PQC 2 50); ponerSaldo $P 0
pagarSesion $PQC $M1 30
check "deuda del paquete (100-30)" "70.00" "$(deudaPaq $PQC)"
post "/api/citas/$M1/anular?devolucion=DINERO&motivo=prueba" '{}' >/dev/null
check "queda 1 sesion"             "1"     "$(sesionesPaq $PQC)"
check "y 50 por cobrar"            "50.00" "$(deudaPaq $PQC)"

echo "== 22. Inasistencia CON devolucion de una sesion de paquete: tampoco deja deuda =="
# El dinero sale por el mismo sitio (revertirDinero), asi que le valia el mismo error. El paciente
# no vino y se le devolvio lo suyo: el paquete deja de cobrar esa sesion, no la pasa a deuda.
PQD=$(paq 2 50); read N1 N2 <<< $(citasDe $PQD 2 50); ponerSaldo $P 0
pagarSesion $PQD $N1 50; pagarSesion $PQD $N2 50
post /api/atenciones/inasistencia "{\"citaId\":$N1,\"motivo\":\"no vino\",\"devolver\":true}" >/dev/null
check "queda 1 sesion" "1"     "$(sesionesPaq $PQD)"
check "sin deuda"      "0.00"  "$(deudaPaq $PQD)"
check "los 50 a favor" "50.00" "$(saldo $P)"

echo "== 23. Anular una sesion que nadie pago no encoge el paquete, y el hueco se reagenda =="
# El paquete solo deja de cobrar una sesion cuando su dinero sale. Sin reembolso no hay nada que
# justifique encogerlo, y anular tiene que seguir liberando el cupo para volver a agendar.
MOD=$(q "SELECT id FROM cat_modalidades LIMIT 1")
# Lo que se mira no es si la cita llega a crearse —eso depende de la agenda del terapeuta, de su
# cupo por franja y de la hora que pida la bateria, y una hora fija se llena a las pocas
# pasadas— sino POR QUE se rechaza. Lo que prueba este caso es el tope del paquete, asi que se
# pregunta exactamente eso: si la respuesta habla del tope o no.
agendarEn() { # $1 paquete -> cuerpo de la respuesta
  curl -s -X POST "$API/api/citas/rapida" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
    -d "{\"pacienteId\":$P,\"terapeutaId\":$T,\"tipoTerapiaId\":$TT,\"tratamientoId\":$1,\"modalidadId\":$MOD,\"fechaInicio\":\"2026-12-01T09:00:00\",\"fechaFin\":\"2026-12-01T09:40:00\",\"duracionMinutos\":40}"
}
porElTope() { case "$1" in *"sesiones agendadas"*) echo "lo frena el tope";; *) echo "el tope lo deja pasar";; esac; }

PQE=$(paq 2 50); read O1 O2 <<< $(citasDe $PQE 2 50)
post "/api/citas/$O1/anular?devolucion=SALDO&motivo=prueba" '{}' >/dev/null
check "sigue siendo de 2 sesiones" "2" "$(sesionesPaq $PQE)"
check "el hueco queda libre"       "1" "$(q "SELECT count(*) FROM sesiones WHERE tratamiento_id=$PQE AND cita_activa_id IS NULL")"
check "y se puede volver a agendar en el" "el tope lo deja pasar" "$(porElTope "$(agendarEn $PQE)")"
# Y donde SI se encogio, el hueco no se vuelve a llenar solo: el paquete del caso 19 bajo a una
# sesion y ya la tiene agendada. Antes se contaban filas de sesion, el hueco liberado se
# reutilizaba y el paquete acababa con 2 sesiones agendadas cobrando una sola.
check "en el encogido ya no cabe otra"    "lo frena el tope"      "$(porElTope "$(agendarEn $PQ)")"

echo
echo "RESULTADO: $OK correctos, $KO fallos"
