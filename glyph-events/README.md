# glyph-events

Glyph Events: cualquier servicio manda un webhook y el Nothing Phone (4a) Pro reproduce una animación en la matriz Glyph con sonido, según reglas que se editan desde una web. Uso personal: un usuario, un teléfono.

```
GitHub / Linear / tu backend ──webhook──▶ /api/hook/<token>  (Next.js en Vercel)
                                              │  guarda el evento crudo (Supabase)
                                              │  lo agrupa con los que llegan en la ventana
                                              │  aplica las reglas: gana la de mayor prioridad
                                              │  respeta el horario de silencio
                                              ▼
                                   FCM (push de datos, prioridad alta)
                                              ▼
                     Plaude Glyph (APK): dibuja la animación y reproduce el sonido
```

- **Backend + web de administración:** [`web/`](web), Next.js en Vercel con Supabase (Postgres + Storage).
- **Esquema:** [`supabase/schema.sql`](supabase/schema.sql).
- **Reproductor:** vive en la app [`glyph-plaude`](../glyph-plaude). No tiene lógica de reglas: recibe la animación completa en el push, así que animaciones y reglas nuevas no requieren actualizar la app.

## Cómo funciona

| Pieza | Detalle |
| --- | --- |
| Fuentes | Cada servicio tiene su URL `https://<app>/api/hook/<token>`. El token es el secreto; no se validan firmas, así sirve cualquier servicio. Acepta JSON, formularios (incluido el `payload` de GitHub) y texto, por POST, PUT, PATCH o GET. |
| Reglas | Condiciones sobre el evento crudo (`headers.x-github-event`, `body.action`, `query.x`, `source`…) con operadores es igual, contiene, empieza con, es uno de, existe, regex. Opcionalmente atadas a una fuente. Cada una elige animación y sonido y tiene prioridad. El webhook nunca elige la animación. |
| Agrupamiento | El primer evento abre un lote que cierra a los N segundos (8 por defecto, configurable de 0 a 50). Todo lo que llega en ese rato entra al mismo lote y suena una sola animación: la de la regla de mayor prioridad que matchee algún evento. El lote se resuelve en la misma función del webhook, con `after()`, cuando vence la ventana. |
| Silencio | Franja horaria con zona horaria. Durante la franja no se manda nada; queda en el historial como silenciado. El botón Probar lo ignora. |
| Sonidos | Se suben desde la web a un bucket privado de Supabase Storage. El push lleva una URL firmada y el id como clave de caché: el teléfono baja cada sonido una vez. Suena con el volumen de notificaciones. |
| Historial | Cada lote con sus eventos crudos, la regla que ganó y qué pasó en el teléfono (enviado, reproducido, silenciado, error). |

### Formato de las animaciones (v1)

```json
{ "v": 1, "frames": ["169 caracteres", "..."], "frameMs": 100, "repeat": 2, "brightness": 100 }
```

Cada cuadro es la matriz de 13×13 fila por fila: `.` apagado y `1` a `9` brillo (9 = lleno). También se aceptan `#` y `+` y cuadros de 13 filas, como en `glyph.json` de Plaude. Hasta 120 cuadros, 40 ms a 10 s por cuadro, y la animación completa dura como mucho 30 s. Si el push supera los 4 KB de FCM, viaja sin la animación y el teléfono la baja de `/api/device/play/<id>`.

En el teléfono, si el toy Plaude está activo la animación se dibuja como overlay del toy (los toys tienen prioridad sobre las apps). Si no, la dibuja un servicio aparte con `setAppMatrixFrame`.

## Puesta en marcha (una vez)

Todo se puede hacer desde el navegador del teléfono.

### 1. Supabase

1. Creá un proyecto en [supabase.com](https://supabase.com) (plan gratis).
2. **SQL Editor › New query**: pegá todo [`supabase/schema.sql`](supabase/schema.sql) y tocá **Run**. Crea las tablas, las funciones y el bucket `ge-sounds`.
3. **Project Settings › API**: copiá la **Project URL** y la clave **service_role** (es secreta: solo va a Vercel).

### 2. Firebase (para el push)

1. Creá un proyecto en [console.firebase.google.com](https://console.firebase.google.com) (sin Analytics alcanza).
2. Agregá una app **Android** con el paquete `com.plaude.glyph`. No hace falta bajar `google-services.json`: la app recibe la configuración al vincularse.
3. **Configuración del proyecto › General**: copiá la **Clave de API web** y, en tu app Android, el **ID de la app** (`1:…:android:…`).
4. **Configuración del proyecto › Cuentas de servicio › Generar nueva clave privada**: baja un JSON.

### 3. Vercel

1. **Add New › Project**, importá `nicolas-palermo/claude-mods` y poné **Root Directory** = `glyph-events/web`.
2. Variables de entorno (ver [`web/.env.example`](web/.env.example)):

| Variable | Valor |
| --- | --- |
| `ADMIN_PASSWORD` | La contraseña que quieras para la web. |
| `SUPABASE_URL` | Project URL de Supabase. |
| `SUPABASE_SERVICE_ROLE_KEY` | Clave service_role. |
| `FIREBASE_SERVICE_ACCOUNT` | El JSON de la cuenta de servicio, entero (o en base64). |
| `FIREBASE_APP_ID` | ID de la app Android (`1:…:android:…`). |
| `FIREBASE_API_KEY` | Clave de API web. |

3. Deploy. Si agregás variables después, hacé **Redeploy**.

### 4. Teléfono

1. Actualizá **Plaude Glyph** desde Obtainium (la versión con Glyph Events).
2. En el teléfono abrí la web, entrá con la contraseña y tocá **Vincular este teléfono**. Se abre la app, baja la config de Firebase y registra el token de push.
3. Tocá **Probar en el teléfono** en Inicio.

### 5. Primeras reglas

1. **Fuentes › Crear fuente** y pegá la URL en el servicio. En GitHub: Settings › Webhooks › Add webhook, Content type `application/json`, sin secret.
2. **Animaciones › Nueva animación**: dibujá cuadro a cuadro (tocar un LED con el mismo nivel lo apaga). También importa lo que exporta el editor de Plaude.
3. **Reglas › Nueva regla**: elegí fuente, condiciones, animación, sonido y prioridad. Abajo se ven los últimos eventos con si matchean, y al abrir uno aparecen sus campos (también como sugerencias al escribir una condición).

Ejemplos de condiciones:

| Quiero | Condición |
| --- | --- |
| Push a main en GitHub | `headers.x-github-event` es igual a `push` y `body.ref` es igual a `refs/heads/main` |
| PR abierto | `headers.x-github-event` es igual a `pull_request` y `body.action` es uno de `opened, reopened` |
| CI falló | `headers.x-github-event` es igual a `workflow_run` y `body.workflow_run.conclusion` es igual a `failure` |
| Issue nuevo en Linear | `body.type` es igual a `Issue` y `body.action` es igual a `create` |

## Desarrollo

```
cd glyph-events/web
npm install
npm test         # reglas, formato de animación y horarios de silencio
npm run build
```

`lib/rules.ts`, `lib/animation.ts` y `lib/quiet.ts` no tienen dependencias y tienen tests. `lib/engine.ts` arma lotes y pushes; `lib/fcm.ts` habla con la API HTTP v1 de FCM sin SDK (JWT firmado con la cuenta de servicio). Las tablas tienen RLS sin políticas: solo el backend, con la service role, las lee.
