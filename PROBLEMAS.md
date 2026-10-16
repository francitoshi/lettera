# Lettera — Problemas de seguridad del diseño

Revisión de seguridad hecha **leyendo el código** (`app/src/main/java/` + las
dependencias reales que usa: `nut-base 0.2.4`, `nut-core 0.2.4`,
`angus-mail 2.0.5`, `h2 2.4.240`). No es un pentest ni una auditoría con
herramientas: cada hallazgo apunta al código y, cuando la afirmación depende de
una dependencia, se indica cómo se comprobó (ver §8).

Documento complementario de `DATA-FLOW.md`, que describe el funcionamiento.
Este documento responde a la otra pregunta: **qué se rompe, por qué importa y
en qué orden arreglarlo**.

---

## 1. Resumen ejecutivo

Lettera tiene una base criptográfica **sólida** (ver §7) pero la capa que decide
**en quién confías** —firma GPG, huella de la clave, verificación del firmante,
transporte TLS— está sin construir, y en varios sitios el código actual la
contradice. Además hay tres fugas de metadatos que traicionan el propósito del
proyecto (huellas GPG y zona horaria en las cabeceras de correo), y el bus de
actores expone los secretos a cualquiera que se suscriba.

| # | Sev. | Hallazgo | Dónde |
|---|------|----------|-------|
| A1 | **Crítica** | No se verifica firma ni firmante al recibir: todo lo que se descifra se muestra como mensaje de un amigo | `TerminalChat.java:658-715`, `MailPoll.java:74-77` |
| A2 | **Crítica** | Sin *pinning* de clave: se cifra al **email**, no a la huella → una clave importada con el mismo uid secuestra el envío | `MailPush.java:51`, `TerminalChat.java:421-440` |
| A3 | **Alta** | No se valida la identidad del servidor TLS (`checkserveridentity` no se activa; en Angus Mail el default es `false`) → MITM posible con cualquier certificado válido de otro dominio | `nut-core/SMTP.java:80-96`, `nut-core/IMAP.java:121-136` |
| A4 | **Alta** | `MailPush` cifra **sin armor** y convierte el binario con `new String(…, UTF8)` → criptograma corrupto, y además no se comprueba el resultado | `MailPush.java:24,51,58` |
| B1 | **Alta** | El asistente deja desactivar el cifrado del canal (`starttls=n`) sin aviso → `AUTH LOGIN` en claro | `TerminalChat.java:828-829` |
| B2 | **Alta** | IMAP no habla STARTTLS y `MailPoll` pasa `starttls` en el hueco de `sslEnable` → con puerto 143 se autentica en claro | `MailPoll.java:57` |
| B3 | **Alta** | `--tor` está roto (NPE): comprueba `proxyOp.isUsed()` dentro de la rama de Tor | `Main.java:212` |
| B4 | **Media** | Sin fijar `mail.*.ssl.protocols` ni `mail.*.ssl.ciphersuites`: se hereda el default del JDK | `nut-core/SMTP.java:80-96` |
| C1 | **Alta** | La BD H2 se cifra con XTS-AES **sin MAC** (modo maleable) y sus valores se leen con `ObjectInputStream.readObject` | `LetteraDb.java:31` + `h2 2.4.240` |
| C2 | **Alta** | El bus no tiene autenticación: cualquiera puede publicar `CryptoUnwrapReq` con su `replyTo` y **desenvolver cualquier secreto** | `CryptoActor.java:60-70` |
| C3 | **Alta** | Permisos de fichero: nada en el proyecto aplica POSIX → `.db`, `.p12`, `.properties` y `log/` quedan con el umask (típicamente 0644) | `Lettera.java:167-222`, `Main.java:274-276` |
| C4 | **Alta** | `--passphrase` visible en `ps`/`/proc/<pid>/cmdline`; `PASS.getKey()` devuelve `String` (imborrable) | `Main.java:165-175`, `nut PASS.java:111` |
| D1 | **Alta** | El asunto lleva la **huella GPG completa** de ambos extremos (y el keyid es la huella cuando la clave se autogenera) | `MailPush.java:57`, `TerminalChat.java:862` |
| D2 | **Alta** | Inyección ANSI en el terminal: el texto descifrado se imprime sin sanear, y `Lettera.stripAnsi()` existe pero **no se usa** | `TerminalChat.java:718-728`, `Lettera.java:271-280` |
| E1 | **Alta** | Sin límite de tamaño ni de rango al leer la bandeja (`inbox.getMessages()` entero, cuerpo a String sin tope) → OOM/DoS desde un remitente cualquiera | `MailPoll.java:68`, `MailBot.java:72` |
| E2 | **Alta** | Nada filtra el remitente antes de descifrar; el enrutado previsto usa la cabecera `From`, que es falsificable | `MailBot.java:41,69-75` |
| E4 | **Alta** | Todo lo que llega a la bandeja se procesa (remitente, asunto, tamaño) sin filtro previo al descifrado | `MailBot.java:69-75`, `MailPoll.java:63-80` |
| F1 | **Media** | `trustLevel` nunca se calcula (se reescribe siempre a 0); las constantes `TRUST_LEVEL_*` no se usan | `TerminalChat.java:913,922,938`, `Lettera.java:58-62` |
| F2 | **Media** | `Friend.sharedSecret` / `sessionKey` se guardan **sin envolver**, a diferencia de `emailPass`/`gpgPassphrase` | `TerminalChat.java:938-941`, `LetteraDb.java:72-78` |
| F3 | **Media** | `PlainNote.id` son epoch-seconds y acaba siendo la clave del mapa `chat:<id>` → un id remoto puede **pisar notas ajenas** y delata horas de composición | `PlainNote.java:15,28`, `LetteraDb.java:103-109` |
| F4 | **Media** | La zona horaria del usuario va en el asunto de cada correo saliente (`…CEST`) | `MailBot.java:49` |
| G1 | **Media** | KDF mejorable (26 iter / 64 MB) y el `.properties` en claro controla esos parámetros → se pueden rebajar a coste 1 | `Config.java:29-31,62-64` |
| G2 | **Media** | Cache del keyring con TTL de 4321 s: una clave revocada o caducada sigue “válida” más de una hora | `Lettera.java:107-132` |
| G3 | **Media** | `waitMillis += 60_000` sin techo en `MailPoll`; bucles `idle()` y mapas en memoria sin cota | `MailPoll.java:60`, `HubBot.java:28-29` |
| G4 | **Media** | El fallo al registrar `StoreActor`/`CryptoActor` se traga: la app sigue funcionando sin cifrado de bus y sin dejar rastro | `Lettera.java:225-233` |
| G5 | **Media** | Apagado incoherente: la BD se cierra dos veces, los actores siguen vivos, el `ActorHub` nunca se cierra | `TerminalChat.java:245`, `Lettera.java:349-373`, `Main.java:217` |
| G6 | **Media** | Traversal de ruta por `--username` sin validar (`<dir>/<username>.p12`…) | `Lettera.java:167-169` |
| G7 | **Media** | Colisión de identidades: `normalize()` funde nombres distintos y el mapa del chat usa el nombre **crudo** mientras el amigo se guarda normalizado → historial partido / mezcla de identidades | `TerminalChat.java:399-445,730-733` |
| H1 | **Baja** | Log a nivel INFO por defecto en fichero (`Log.WARN=3` → fichero en 2): el username acaba en `lettera.log` | `Main.java:153,268-281` |
| H2 | **Baja** | `char[]` con vida larga sin limpiar (`unwrapKey` en `startChat`, la `seed`, los `SecureChars` de SMTP/IMAP) | `Lettera.java:188-198,253-254` |
| H3 | **Baja** | No hay rotación de clave ni cambio de passphrase (`changePassphrase` comentado, `PASS.setKey` sin usar) | `PassphraseManager.java:76-88` |
| H4 | **Baja** | Buzón abierto en modo lectura-escritura (`readonly=false`): la app puede marcar/modificar/borrar mensajes ajenos | `MailPoll.java:57`, `nut-core/IMAP.java:133` |
| H5 | **Baja** | La GUI web (`resources/web/`) es un prototipo huérfano que llama a un `javaBridge` inexistente; si se conecta, es una superficie de ejecución Java↔JS sin autenticación ni CSP | `resources/web/app.js` |

Leyenda: **Crítica** = compromises la propiedad que la app promete (confidencialidad
o autenticidad del interlocutor); **Alta** = pérdida de confidencialidad/identidad
alcanzable en escenarios reales; **Media** = requiere condición adicional o es
degradación/robustez con impacto de seguridad; **Baja** = higiene y defensa en
profundidad.

---

## 2. Modelo de amenazas (qué puede hacer cada atacante)

| Actor | Puede | Impacto esperado en lettera |
|---|---|---|
| **Remitente de correo no autenticado** | mandar un email a tu buzón, FROM elegido por el atacante, cuerpo de tamaño arbitrario, adjuntos | que tu cliente procese lo que quiera: DoS de memoria/CPU, ruido en el terminal, intento de suplantar a un amigo |
| **Proveedor de correo / relay** | ver cabeceras, tamaño y hora de cada mensaje; con credenciales, leerlos todos | obtiene el **grafo social** (quién habla con quién) y, por el asunto (§D1), las **huellas GPG** de ambos extremos |
| **Atacante activo en la ruta** (Wi-Fi hostil, DNS poisoned, salida Tor) | presentar su propio servidor SMTP/IMAP | con §A3 (sin verificar el nombre del certificado) obtiene **MITM completo**: credenciales, criptograma y capacidad de **inyectar notas** |
| **Dueño de la clave de un amigo comprometido** | descifrar todo el histórico recibido de esa persona | no hay rotación de claves ni secreto por sesión (§F3, §H3): todo el pasado queda expuesto |
| **Otro usuario del mismo sistema** | leer `~/.lettera/*` (permisos por umask) | lee salt y KDF params; con la `.db` cifrada necesita además la passphrase, pero **puede escribir** los ficheros → §C1 (manipular) y §G1 (degradar el KDF) |
| **Proceso/dependencia en el classpath** | suscribirse al bus | extrae **cualquier secreto envuelto** (§C2) y la lista de amigos |
| **Lector de pantalla / logs / core dumps** | ver texto y metadatos | exposición de contenido si se registra o se filtra una nota |

---

## 3. Identidad y autenticación del interlocutor (lo más grave)

### A1 · Crítica · No se verifica ni la firma ni el firmante

Al recibir, el código **descifra y muestra** sin mirar el resultado de la
verificación:

- `TerminalChat.printMessage` llama a `GPG.decryptAndVerify(body, gpgPass, status)`
  con un `status` que **se descarta** (`TerminalChat.java:665-668`); la línea que
  lo imprimiría (`reader.printAbove(currentChat.friendName+"> "+plaintext)`) está
  comentada.
- `MailPoll` desenvuelve la passphrase y, en el `//666 hub.send(...)`, no llega a
  construir nada (`MailPoll.java:74-77`).
- `MailBot` lee `From`, `To` y cuerpo pero el `SealedNote` queda en un
  comentario (`MailBot.java:69-75`).

**Impacto.** La autenticidad del mensaje descansa en la **cabecera `From`**, que
cualquiera puede escribir. Quien consiga insertar texto en tu buzón puede
inyectar notas que se presentan como de un amigo tuyo, sin que ninguna capa lo
detecte. `GPG.DecryptStatus` ya trae `getSigner()`, `isValidSignature()`,
`isDecryptionOkay()`, `recipients`, `sigDate`… y no se consulta en ningún sitio.

**Arreglo.** Regla única: si `!status.isDecryptionOkay() || !status.isValidSignature()`
→ descartar; luego `status.getSigner()` debe coincidir con `Friend.fingerprint`;
si no coincide → subir `trustLevel` a `TRUST_LEVEL_KEY_MISMATCH_DANGER` y no
mostrar. La verificación debe ocurrir **antes** de tocar la base de datos.

### A2 · Crítica · Sin pinning: se cifra al email, no a la huella

`MailPush.sendNote` cifra con `GPG.encryptAndSign(plain, sender.email, gpgPass, friend.email)`,
es decir **`--recipient <email>`** (`MailPush.java:51`). GnuPG resuelve esa cadena
contra el keyring local y cifra para *la clave que encuentre*. Si alguien introduce
en tu keyring una clave con el uid `amigo@ejemplo.org` (adjuntada en un email,
“keyserver refresh”, `gpg --import`…), tus mensajes se cifran para esa clave y sólo
ella puede leerlos.

El chequeo de huella existente tampoco cierra el hueco:

- `Friend.fingerprint` es **opcional**: `Strings.isNotEmpty(friend.fingerprint) &&
  !friendFingerprints.contains(...)` (`TerminalChat.java:436`) sólo valida si está
  informado.
- `getFingerprints(email, pubs.get())` (`Lettera.java:410-426`) compara contra las
  claves **que ya están en tu keyring**; una clave recién importada con el mismo
  email **casa**.
- `HubBot.plainToSealed` sí usa huellas (`plain.innerFingerprint`,
  `plain.outerFingerprint`, `HubBot.java:87`), pero devuelve `null` — no ejecuta.

**Arreglo.** `fingerprint` obligatorio para poder enviar; cifrar a la huella
(`--recipient <fingerprint40>`) y verificar con `--assert-signer <fingerprint>`
o comparando `status.getSigner()`. El email es sólo transporte.

### F1 · Media · `trustLevel` es decorativo

`Lettera` define cinco niveles (`TRUST_LEVEL_KEY_MISMATCH_DANGER` … 
`TRUST_LEVEL_VERIFIED_JPAKE`, `Lettera.java:58-62`), pero `setupFriend` lee el
valor, lo copia y lo vuelve a guardar en `0` (`TerminalChat.java:913,922,938`).
Nunca se consulta para decidir nada: no hay TOFU, ni verificación fuera de banda,
ni revocación. Las dependencias **ZXing y qrcodegen ya están en `build.gradle`
y no se usan en ninguna parte**: están justo para el QR de verificación de
huellas que falta.

**Arreglo.** Calcular el nivel de confianza con datos reales: huella confirmada
fuera de banda → `VERIFIED_MANUAL`; reto firmado (SKIP/JPAKE ya está en `nut`) →
`VERIFIED_JPAKE`; y usarlo como **política** (rechazar o avisar), no como
decoración.

### E2 · Alta · Enrutado por `From` falsificable

`MailBot` se suscribe al topic `from` (la dirección propia) pensando en enrutar
por remitente (`MailBot.java:41`), y el listener calcula `Arrays.toString(msg.getFrom())`
(`MailBot.java:70`). Una cabecera `From` forjada decide a qué sesión entra el
mensaje. La identidad real debe salir de la **firma GPG**, nunca de la cabecera.

**Arreglo.** La clave de enrutado debe derivarse de la firma
(`status.getSigner()`), no de la cabecera; el `From` sólo sirve como filtro barato
previo, nunca como identidad.

### F3 · Media · `PlainNote.id` es el reloj y es la clave del mapa

`PlainNote` usa `LongNonce.getEpochSecondInstance()` (`PlainNote.java:15`), es
decir `max(último+1, epochSecond)`: el id **es la hora de composición** y acaba
siendo la **clave primaria** de `chat:<id>` (`LetteraDb.java:103-109`). Dos
consecuencias:

- **Integridad**: cuando `sealedToPlain` se implemente, el `id` del `SealedNote`
  remoto llegará al `PlainNote` local (`id!=0 ? id : NONCE.get()`), y como es la
  clave del mapa, un remitente puede **sobrescribir notas existentes** o colisionar
  con ellas. Un id remoto no debería poder decidir la clave de almacenamiento.
- **Metadatos**: el `.db` cifrado sigue siendo un índice legible por anyone que lo
  tenga abierto de un vistazo a los ids, y `created/showed/stored/sent` (longs)
  quedan como marcas de tiempo.

**Arreglo.** Id opaco y aleatorio (`Long.random` / UUIDv7) para el almacenamiento;
llevar el tiempo en un campo aparte; validar el rango del id remoto y rechazar
colisiones.

---

## 4. Transporte: TLS y Tor

### A3 · Alta · No se verifica el nombre del servidor en el certificado

`nut-core` monta las propiedades de conexión a mano y **no** pone
`mail.smtp.ssl.checkserveridentity` ni `mail.imap.ssl.checkserveridentity`:

- SMTP: sólo `mail.smtp.auth`, `mail.smtp.starttls.enable`, `mail.smtp.host`,
  `mail.smtp.port` (`nut-core/SMTP.java:80-96`).
- IMAP: sólo `mail.store.protocol`, `mail.imap.host`, `mail.imap.port`,
  `mail.imap.ssl.enable` (`nut-core/IMAP.java:121-126`).

Comprobé en el jar real (`angus-mail 2.0.5`) que la comprobación depende de esa
propiedad y que **por defecto está desactivada**: `SocketFetcher` la lee con
`PropUtil.getBooleanProperty(props, "mail.<proto>.ssl.checkserveridentity", false)`
y sólo si es `true` llama a `SSLSocket.setEndpointIdentificationAlgorithm("LDAPS")`.
Es decir: el certificado se valida contra el almacén de confianza, pero **nadie
comprueba que sea el certificado de `smtp.gmail.com`**. Con eso, cualquier
atacante en la ruta con un certificado **legítimo de otro dominio** (Let's Encrypt
es gratis) cierra el TLS contra ti, se lleva tu contraseña del correo, lee el
criptograma y puede **inyectar notas** (combinado con A1).

**Arreglo.** `mail.smtp.ssl.checkserveridentity=true`,
`mail.imap.ssl.checkserveridentity=true`, `mail.smtp.ssl.protocols=TLSv1.3,TLSv1.2`
y, si procede, `mail.imap.ssl.protocols`. Es un cambio de una línea en cada
conexión de `nut-core` y de nada más.

### B1 · Alta · Se puede apagar el cifrado del canal, sin avisar

El asistente pregunta `auth[Y/n]` y `starttls[Y/n]` como sí/no libres
(`TerminalChat.java:828-829`), con `auth=true, starttls=true` sólo como valor
inicial. Si el usuario contesta `n` a `starttls`, el `AUTH LOGIN/PLAIN` viaja en
claro. No hay `mail.smtp.starttls.required`, ni aviso, ni lista de cifrados, ni
comprobación posterior de que el canal negociado fuera TLS.

**Arreglo.** Si `auth` es verdadero, exigir STARTTLS o `sslEnable` (error, no
warning); poner `mail.smtp.starttls.required=true`; registrar en el `Sender` el
transporte real usado y rechazarlo si no coincide.

### B2 · Alta · IMAP: sin STARTTLS y con los argumentos cruzados

`nut-core/IMAP` sólo sabe hacer SSL implícito (`mail.imap.ssl.enable`); no hay
`mail.imap.starttls.enable`. Peor: `MailPoll` construye el reader pasando
`sender.starttls` en el parámetro **`sslEnable`**:

```java
// MailPoll.java:57
new IMAP(sender.imapHost, sender.imapPort, sender.auth, sender.starttls, false, sender.username, secureEmailPass);
//                     host          port          auth  ← sslEnable ← starttls
```

Con `starttls=n` y puerto 143, **la autenticación IMAP va en claro**. Con 993
funciona por casualidad (SSL implícito sobre el puerto correcto).

**Arreglo.** Renombrar los parámetros para que no puedan cruzarse, añadir
STARTTLS a `nut-core/IMAP`, y validar host/puerto/cifrado contra el preset.

### B3 · Alta · `--tor` está roto (y es la ruta del anonimato)

```java
// Main.java:210-215
else if (torOp.isUsed())
{
    HostPort hostPort = proxyOp.isUsed() ? getHostPort(proxyOp, TOR_HOST_PORT) : null;  // ← proxyOp, no torOp
    Tor tor = Tor.managed(hostPort.port, SocksPolicy.LOCALHOST_ONLY);                     // ← NPE
```

`proxyOp.isUsed()` es falso cuando se usó `--tor`, luego `hostPort` es `null` y
la app **revienta con NPE** antes de instalar Tor. La protección de anonimato no
llega a aplicarse nunca. Si además se arregla el NPE, falta una comprobación de
que el SOCKS está realmente disponible antes de enviar: `installGlobally()` es
global al JVM, pero nada obliga a que SMTP/IMAP lo usen, y el subproceso `gpg`
queda fuera.

**Arreglo.** `getHostPort(torOp, TOR_HOST_PORT)`; verificar `Tor`/`Socks5` con un
handshake de prueba; y **no enviar nada** si el proxy no está listo (fallar
cerrar, no reintentar sin proxy). Documentar explícitamente que `gpg` no va por Tor
(no importa: sólo toca el keyring local).

### B4 · Media · Sin referencias de TLS fijadas

No se fija `mail.*.ssl.protocols` ni `mail.*.ssl.ciphersuites`; se hereda el
default del JDK. Aceptable hoy, pero conviene pinear TLS 1.3/1.2 y una lista
explícita para que un cambio de JDK no debilite nada.

**Arreglo.** `mail.smtp.ssl.protocols=TLSv1.3,TLSv1.2` (y lo mismo para IMAP),
más una lista explícita de `ciphersuites` si se quiere ir más allá.

### A4 · Alta · El criptograma viaja corrupto

`MailPush` usa su propia instancia `private static final GPG GPG = new GPG();`
(`MailPush.java:24`), **sin `setArmor(true)`** —a diferencia de
`Lettera.java:66` y `HubBot.java:23`, que sí lo activa—, y convierte el resultado
binario con `new String(encryptedText, UTF8)` antes de mandarlo
(`MailPush.java:58`). El resultado es doble fallo:

- GPG devuelve bytes arbitrarios; al pasar por `String`/`UTF8` se sustituyen bytes
  no representables → el criptograma llega **alterado** y el destinatario no podrá
  descifrarlo (fallo silencioso: el envío “tiene éxito”).
- Sin armor el cuerpo del correo es binario, que los MTAs y clientes ajenos
  recortan o transforman (y `smtp.send` lo mete como texto con
  `message.setText`).

**Arreglo.** `new GPG().setArmor(true)` en `MailPush`, y enviar el texto armorado
como cadena (armor es ASCII) o mejor bytes, nunca `new String(binario, UTF8)`.
Aplica igual a `HubBot`/`MailBot`, que deben compartir una única instancia
configurada.

---

## 5. Datos en reposo, en memoria y en el bus

### C1 · Alta · La BD cifrada no está autenticada y deserializa Java

`LetteraDb` abre el MVStore de H2 con `encryptionKey` (`LetteraDb.java:31`).
Verifiqué en `h2-2.4.240.jar` qué cifrado usa realmente:

- `org.h2.store.fs.encrypt.FileEncrypt` + `XTS`, con `BLOCK_SIZE = 4096`,
  `SALT_LENGTH = 8`, `HASH_ITERATIONS = 10` y `org.h2.security.BlockCipher`.
- Es **XTS-AES**: un modo **determinista y sin MAC**. No hay autenticación.

Consecuencias:

- **Manipulación silenciosa**: quien pueda escribir el `.db` puede alterar bloques
  de texto de forma predecible (XTS es maleable) sin que nada lo detecte: los
  chats mostrados dejarían de ser lo que se escribió.
- **Riesgo de deserialización**: los mapas se abren sin tipo
  (`store.openMap("sender")`), así que H2 usa `ObjectDataType`, que deserializa
  con `ObjectInputStream.readObject` (verificado en
  `org/h2/mvstore/type/ObjectDataType.class`). Una BD manipulada es, por tanto,
  una entrada de **deserialización de Java** con las dependencias del proyecto
  (BouncyCastle, H2, snakeyaml, JLine…) en el classpath.

A favor: la contraseña de la BD son 32 bytes aleatorios (`Lettera.java:212`), no
una passphrase humana, así que el `HASH_ITERATIONS=10` de H2 no es un problema
practicable aquí. El problema es la **integridad**, no la confidencialidad.

**Arreglo.** Capa de AEAD propia (AES-GCM por registro, clave derivada de la seed
de Argon2) sobre el fichero, o un contenedor cifrado y autenticado que envuelva
el MVStore; y sustituir `ObjectDataType` por un `DataType` explícito sin
deserialización.

### C2 · Alta · El bus es la puerta de atrás de los secretos

`ActorHub` no tiene autenticación ni autorización: los topics son `String`. Y el
API de cripografía está construido justo para regalar los secretos:

```java
// CryptoActor: cualquier publisher puede pedir cualquier secreto y que se lo
// entregue a su propio callback.
hub.pub("CryptoUnwrapReq").accept(
    new CryptoActor.UnwrapReq("gpg", "pass", sender.gpgPassphrase, miConsumer));
// → miConsumer.accept(char[] con la passphrase GPG en claro)
```

- `UnwrapReq.replyTo` es un `Object` que se castea a `Consumer` (`CryptoActor.java:60-70`).
- `UnwrapResp` lleva `char[] password` y se publica en `CryptoUnwrapResp`; basta
  con suscribirse a ese topic para recibir contraseñas en claro
  (`CryptoActor.java:36-40`).
- Lo mismo con `StoreGetFriend.replyTo` / `StoreFriendResp` (`StoreActor.java:28-38`).

El daño real depende de quién más esté en el JVM (dependencia, plugin, otro
proceso), pero el diseño no pone ninguna barrera: **el bus debería ser un bus, no
un almacén de secretos**.

**Arreglo.** Que `CryptoActor` no devuelva nunca material de clave por el bus:
o bien devuelve el resultado **ya usado** (p. ej. pide “descifra y envía por
MailPush”), o bien devuelve un handle opaco. Si hace falta asincronía, cifrar la
respuesta con la misma seed y limitarla a una clave de sesión. Tipar `replyTo`
como una interfaz propia y registrar los callbacks permitidos en vez de aceptar
callables arbitrarios.

### C3 · Alta · Permisos de fichero por defecto (umask)

En todo el proyecto no hay ni una llamada a `setReadable`, `setWritable`,
`setPosixFilePermissions` ni similar (verificado con grep: cero coincidencias).
Así que `.db`, `.p12`, `.properties` y `log/lettera.log.*` se crean con el umask
del proceso (típicamente `0644`): **legibles por cualquier otro usuario del
sistema**. El directorio de datos en modo snap cae en un temporal compartido
(`Snap.fixTmpDir()`, `Main.java:146`).

**Arreglo.** `~/.lettera` a `0700` y los ficheros a `0600` al crearlos
(`Files.setPosixFilePermissions` o `File.setReadable(false, false)`), con
verificación al abrir (y aviso si están más abiertos de lo debido).

### C4 · Alta · Passphrases en la línea de órdenes y en el heap

- `--passphrase` / `-p` (`Main.java:165-175`): visible en `ps`, en el historial del
  shell y en `/proc/<pid>/cmdline` mientras el proceso viva. Un `String` de Java es
  además imborrable. Es la vía que usan los tests y el modo batch.
- `PASS.getKey(path)` devuelve un `String` (`nut PASS.java:111-136`); ese `String`
  es el **valor del secreto GPG-cifrado de `pass`**, inmortal en el heap; luego
  `.toCharArray()` crea una segunda copia que sí se limpia.
- El propio `nut PASS` devuelve `String` en toda su API, así que el diseño no
  ofrece una alternativa `char[]`.

**Arreglo.** Dejar `--passphrase` sólo para entorno controlado (documentarlo como
tal), preferir `--pass-path` o lectura desde stdin sin eco; y añadir al camino
seguro una API `char[]` en `nut` (`getKeyChars`).

### F2 · Media · Los secretos de sesión no se envuelven

`KeyWrapper` protege `emailPass` y `gpgPassphrase`, pero `Friend.sharedSecret` y
`Friend.sessionKey` se guardan **tal cual** en el `.db` (`TerminalChat.java:936-941`,
`LetteraDb.java:72-78`). Son precisamente los secretos quebtn permiten derivar el
cifrado de una sesión; deserves el mismo AES-GCM que el resto.

**Arreglo.** Envolverlos con `keyWrapper.wrapKey("friend","sharedSecret", …)` /
`("friend","sessionKey", …)`.

### H2 · Baja · `char[]` con vida larga sin limpiar

- `Lettera.startChat` desenvuelve dos claves y construye `SecureChars` sin
  limpiar los `char[]` originales (`Lettera.java:253-254`).
- La `seed` de Argon2 se queda en el heap durante toda la sesión sin
  `Arrays.fill` (`Lettera.java:190`).
- `MailPush`/`MailBot` guardan el `SecureChars` del correo para toda la vida del
  objeto (correcto mientras el objeto vive; no hay apagado explícito).

Los caminos calientes sí lo hacen bien (`MailPush.java:52`, `MailPoll.java:77`,
`KeyWrapper.java:28,37`), así que es una inconsistencia, no una ausencia de
conciencia.

**Arreglo.** Envolver siempre en `try/finally` con `Arrays.fill(chars,0)`; limpiar
la `seed` al final de `open()`; y un `close()` explícito para los `SecureChars` de
SMTP/IMAP.

### F4 · Media · La zona horaria viaja en cada asunto

```java
// MailBot.java:49
String subject = "lettera " + ZonedDateTime.now().format(JavaTime.YYYY_MM_DD_HH_MM_SSz);
```

Lo comprobé ejecutando ese `DateTimeFormatter` (`yyyy-MM-dd HH:mm:ssz`): produce
`lettera 2026-10-06 00:25:13CEST`. Cada mensaje enviado revela la zona horaria del
usuario a cualquier observador de cabeceras (proveedor, relay, servidor de
destino). Es un vector de deanonimización gratuito en un proyecto cuyo objetivo es
comunicación anónima.

**Arreglo.** Un asunto opaco y estable: `"lettera <id-opaco>"`, sin hora ni zona.
El timestamp va en `Date`, que el transporte ya pone, o dentro del mensaje
cifrado.

### D1 · Alta · Huellas GPG y grafo social en las cabeceras

```java
// MailPush.java:57
String subject = "lettera " + sender.gpgKeyId + "-" + friend.fingerprint;
```

Y `gpgKeyId` es **la huella completa de 40 hex** cuando la clave se autogenera en
el asistente (`TerminalChat.java:862`: `gpgKeyId = keys[0].getMain().getFingerprint()`).

Consecuencias:

- El proveedor de correo obtiene la **huella GPG de ambos extremos** de cada
  conversación: eso identifica cuentas de forma estable (un pseudónimo por
  clave) y permite correlacionar conversaciones y dispositivos.
- Combinado con `To:`/`From:` (que son inevitables en correo), queda el **grafo
  social** completo ante el proveedor. Con `--tor` roto (§B3) ese tráfico sale por
  la red real.

**Arreglo.** Normalizar `gpgKeyId` (guardar el keyid corto, no la huella) o no
mandarlo; asunto opaco (§F4); y desactivar/enrutar elSubjects de lettera con un
prefijo fijo que noporte metadatos.

### G1 · Media · KDF mejorable y parámetros manipulables

`Config` usa 26 iteraciones, 64 MB y paralelismo ≤4 (`Config.java:29-31`). Está en
el orden de magnitud correcto (OWASP sugiere Argon2id con m≥46 MiB y t≥2), pero
`iterations` y `memory_kb` **se leen de un `.properties` en claro** y sin
permisos ni firma (`Config.java:62-64`, `Config.createDefault`). Quien pueda
escribir ese fichero baja el coste a `iterations=1` y convierte un ataque offline
de passphrase en trivial. La propia GUI sugiere “4 o 5 palabras” y acepta
`StrongPassword.Level.Good`, lo que apunta a **ataque por diccionario** más que
por fuerza bruta.

**Arreglo.** Fijar el coste en código (o acotarlo por arriba y por abajo al leer),
firmar o considerar el `.properties` parte del estado cifrado, y empezar a
rechazar passphrases con palabras de diccionario comunes.

---

## 6. Entrada no confiable: correo, terminal, CLI

### D2 · Alta · Inyección de secuencias ANSI en el terminal

El texto descifrado se imprime tal cual:

```java
// TerminalChat.java:718-728
public void accept(PlainNote note)
{
    if (reader != null) reader.printAbove(note.text);   // ← texto remoto, sin sanear
    ...
}
```

y `printMessage` imprime cuerpos **HTML crudos** de la bandeja (`TerminalChat.java:673-707`).
Existe un helper exactamente para esto, `Lettera.stripAnsi()`
(`AttributedString.stripAnsi`, `Lettera.java:271-280`), y **no se usa en ninguna
parte** (verificado por grep).

Con esto, quien consiga que se muestre una nota (o un HTML del buzón) puede: mover
el cursor, borrar líneas, **simular el prompt y el banner de lettera**, escribir
texto falso (p. ej. “sesión cerrada, tus mensajes están a salvo”), esconder líneas
reales, o crear enlaces OSC-8 clicables que apuntan a sitios maliciosos. Es una
técnica clásica y subestimada en CLIs; en una app cuyo valor es la confianza en
lo que muestra, es grave.

**Arreglo.** Sanear siempre lo que venga de la red antes de imprimir:
`Lettera.stripAnsi(note.text)` o imprimir con `AttributedString` forzado a plano;
nunca imprimir HTML (extraer texto o ignorar); y en `MockTerminal`, filtrar
también cualquier byte de control.

### E1 · Alta · Sin límites de tamaño ni de rango al leer la bandeja

- `MailPoll` llama `mailReader.getMessages()` **sin rango** cuando no hay marca de
  agua, y `nut-core/IMAP.getMessages()` hace `inbox.getMessages()` → **el buzón
  entero** (`MailPoll.java:68`, `nut-core/IMAP.java:177-184`).
- `MailBot` lee el cuerpo entero a memoria sin tope:
  `IO.readInputStreamAsString(msg.getInputStream(), UTF_8)` (`MailBot.java:72`).

Un remitente cualquiera (nada filtra antes) puede mandar un adjunto de 2 GB y te
provoca OOM; y cada mensaje dispara un **subproceso `gpg`** de descifrado
(coste de CPU y fork). `MailPoll` además llama `Toolkit.getDefaultToolkit().beep()`
(`MailPoll.java:78`) → efecto de interfaz **disparado por control remoto** (y
AWT en entornos headless).

**Arreglo.** `getMessages(1, lastUID)` ( MailBot ya rastrea `uidValidity/lastUID`
correctamente: usar eso en `MailPoll` en vez de `ReceivedDateTerm`), un
`maxMessageBytes` (p. ej. 1 MiB) con `InputStream` acotado, un máximo de mensajes
por ciclo, y filtrar remitente/tamaño **antes** de invocar a GPG.

### E4 · Alta · Se procesa lo que llegue, sin filtrar

No hay ninguna comprobación previa de remitente, asunto o tamaño antes de
descifrar (y §A3 impide saber quién es el remitente real). Combinado con E1 y
A1: el camino de recepción es, hoy, el más expuesto.

**Arreglo.** Orden correcto: (1) remitente esperado, (2) tamaño, (3) descifrar,
(4) verificar firma + firmante, (5) persistir y mostrar.

### G3 · Media · Backoff sin techo y estructuras sin cota

- `MailPoll`: `waitMillis += 60_000` cada ciclo, sin límite (`MailPoll.java:60`);
  nunca se resetea salvo `sync()` → a la hora 12 duerme 12 minutos.
- `MailBot.run()` hace `imap.idle()` en bucle sin espera ni `waitForIdle`.
- `HubBot.chats` y `sealedNotes` son `HashMap` sin límite (`HubBot.java:28-29`). y las notas no tienen tamaño máximo: una sola nota gigante queda residente en memoria.
- `LetteraDb.openChat(id)` crea un mapa del store por cada nombre tecleado
  (`LetteraDb.java:103-109`): un usuario (o un script con `--input`) puede
  inflar el número de mapas.

**Arreglo.** Tope de espera, backoff exponencial con jitter, y límites de tamaño
de mapas / notas / longitud de id.

### G2 · Media · Cache del keyring de hasta 72 minutos

`Lettera.TTL = 4321` s sobre `Supplier<SecKey[]>` y `Supplier<PubKey[]>`
(`Lettera.java:107-132`). Una clave **revocada**, **caducada** o recién importada
puede seguir considerándose válida durante más de una hora dentro de la misma
sesión; y una clave revocada fuera de línea no se nota nunca.

**Arreglo.** TTL corto (segundos) o sin caché; invalidar explícitamente tras
`genKey`/`addKey`/import; comprobar `REVKEYSIG`/expiración en la decisión de
confianza.

### G4 · Media · Fallo silencioso al registrar los actores

`Lettera.open` envuelve la creación de `StoreActor` y `CryptoActor` en
`try { … } catch (Exception e) { }` (`Lettera.java:225-233`). Si el bus no está
available, la app **sigue funcionando** con rutas en claro, sin cifrado de bus y
sin dejar rastro en el log. Un fallo de seguridad debe ser ruidoso y, si afecta a
criptografía, abortar.

**Arreglo.** Loguear el fallo (y contexto); si el actor de cripto no está
disponible, no continuar.

### G5 · Media · Apagado incoherente

`TerminalChat.run()` cierra la BD en su `finally` (`TerminalChat.java:245`)
mientras los actores siguen vivos; después `Lettera.close()` la cierra **otra vez**
(`Lettera.java:372`); y el `ActorHub` —creado en `Main.java:217` fuera del
`try-with-resources`— **nunca se cierra**. Efecto: actores escribiendo en una base
cerrada, y hilos no-daemon que pueden impedir la salida limpia del proceso.

**Arreglo.** Un único punto de cierre (el `close()` del `AutoCloseable`), con
orden: parar actores → drenar el hub (`shutdown(true)`) → cerrar la BD → cerrar
los publishers.

### G6 · Media · `--username` sin validar

`<dir>/<usuario>.properties|.p12|.db` (`Lettera.java:167-169`) se construyen sin
validar el nombre: con `-u ../../destino` se escriben ficheros fuera del
directorio de datos (sobrescritura o creación arbitraria dentro del directorio
padre).

**Arreglo.** Validar `--username` con una expresión estricta (`^[A-Za-z0-9_-]+$`)
y rechazar cualquier ruta; idealmente, canónicos contra `letteraDir`.

### G7 · Media · Identidades que colisionan y chats que se mezclan

`normalize()` (`TerminalChat.java:730-733`) funde nombres distintos
(“John Doe” → `john_doe` colisiona con un amigo llamado literalmente `john_doe`),
y `chat()` usa el nombre **crudo** como clave del mapa (`db.openChat(name)`,
`TerminalChat.java:442`) mientras `setupFriend()` guarda el **normalizado**
(`TerminalChat.java:938`). Dos consecuencias: el historial se parte en trozos según
cómo se teclee el nombre, y dos identidades distintas pueden acabar mapeando al mismo
`chat:<id>`. Un chat mezclado es una fuga de mensajes entre conversaciones.

**Arreglo.** Un identificador único e inmutable por contacto (el `name`
normalizado, o mejor la huella), usado **igual** como clave de amigo y de mapa de
chat; y rechazo de colisiones al crear.

### H1 · Baja · Log con metadatos a nivel INFO por defecto

`Log.WARN=3`, y `setupLoggers` deja la consola en 3 y **el fichero en 2 (INFO)**
(`Main.java:268-281`). `Main` loguea el username a INFO (`Main.java:161`) →
el username acaba en `lettera.log.*` (que además está en 0644, ver C3).
`HubBot` instancia GPG con `setDebug(true)` (`HubBot.java:23`), que puede volcar
metadatos de claves en el log.

**Arreglo.** Fichero en WARNING por defecto; `chmod 600` en el log; no loguear
identificadores en INFO; `setDebug(false)` salvo sesión de depuración explícita.

### H3 · Baja · Sin rotación de clave ni cambio de passphrase

`PassphraseManager.changePassphrase` está comentado (`PassphraseManager.java:76-88`)
y `PASS.setKey` no se usa. No hay forma de cambiar la passphrase maestra ni de
rotar la clave GPG; y como las notas se cifran directamente a la clave estática del
destinatario, una clave compromise revela **todo** el histórico recibido de esa
persona. `SecureWrapper` hace el trabajo de re-envoltura trivial (el secreto se
re-encierra con la seed nueva), así que la migración sería barata.

Tampoco hay rotación del `salt` de Argon2 ni de la contraseña de la BD: cambiar la
passphrase no es posible y una contraseña de BD filtrada sigue sirviendo (con
`ObjectDataType` encima, §C1).

**Arreglo.** Reactivar `changePassphrase` reenvolviendo los secretos con
`KeyWrapper` (seed nueva, mismo `info`), y una orden de rotación de clave GPG con
re-cifrado del `Sender` y aviso de que el histórico enviado sigue dependiendo de
la clave antigua.

### H4 · Baja · Buzón abierto en lectura-escritura

`MailPoll` abre el INBOX con `readonly=false` (`MailPoll.java:57`) →
`Folder.READ_WRITE` (`nut-core/IMAP.java:133`). La app puede marcar como leídos,
mover o borrar mensajes sin que el usuario lo note. Lo típico en un cliente de
correo sería `READ_ONLY` + opcionalmente `\Seen`.

**Arreglo.** `readonly=true` por defecto; banderas IMAP explícitas sólo si se
muestran mensajes nuevos.

### H5 · Baja · La GUI web, si revive, es una superficie de ejecución

`resources/web/{index.html,app.js}` es un prototipo huérfano (“GZip Tool”) que
llama a un `javaBridge.zip()/unzip()/exit()` que no existe. Hoy es inocuo; si se
conecta, cualquier puente Java↔JS es ejecución de código por definición, y sin
autenticación del `webview` cualquier HTML abierto puede invocarlo.

**Arreglo.** Si se resucita: superficie mínima (sin operaciones de escritura de
secretos), CSP estricta, sin `innerHTML` con datos externos, y validación de que el
contenido servido es propio.

---

## 7. Lo que sí está bien (para calibrar el riesgo real)

- **`SecureWrapper` es criptografía bien hecha**: AES-256-GCM con **salt e IV
  aleatorios por operación** y el `info` (`purpose+"+"+name`) **vinculado
  criptográficamente** (se compara al desenvolver, `SecureWrapper.unwrap`:
  “Mismatched info context”). La separación de dominio entre `email+pass` y
  `gpg+pass` es correcta y un blob no se puede desencriptar con el propósito
  equivocado.
- **La seed de Argon2 nunca se persiste**; se recalcula en cada arranque. El
  `<usuario>.p12` protege la contraseña de la BD con HKDF-SHA512. La contraseña de
  la BD es 32 bytes **aleatorios**, así que el KDF débil de H2 es irrelevante.
- **`SecureChars`** en lugar de `String` para contraseñas SMTP/IMAP, y
  `Arrays.fill(…, '\0')` en los caminos calientes (`MailPush.java:52`,
  `MailPoll.java:77`, `KeyWrapper.java:28,37`).
- **GPG se invoca con `PINENTRYMODE_LOOPBACK` + `--passphrase-fd 0`**: la
  passphrase viaja por un pipe, no por `argv` (visible en `ps`). Y se usa
  **Argon2id**, no Argon2i/d.
- **`PASS` usa `Runtime.exec(String[])`**, sin shell → **no hay inyección de
  comandos** con `--pass-path` (lo verifiqué).
- **`--input` pasa por `ThrottledInputStream`** (66 bytes / 200 ms): frena el
  tecleo automatizado en modo no interactivo.
- **La UI y los actores están desacoplados**: la UI no habla con SMTP/IMAP, lo que
  hace fácil (y por qué vale la pena) poner todas las validaciones en el camino de
  entrada. La topología del bus es la correcta; lo que falta es la política.

---

## 8. Cómo se verificaron los puntos dependientes de dependencias

| Afirmación | Cómo se comprobó |
|---|---|
| `checkserveridentity` por defecto **false** en Angus Mail 2.0.5 | `javap -c org/eclipse/angus/mail/util/SocketFetcher.class`: `PropUtil.getBooleanProperty(props, ".ssl.checkserveridentity", false)` y `setEndpointIdentificationAlgorithm("LDAPS")` sólo si es `true` |
| H2 cifra con **XTS sin MAC** | `javap` de `org/h2/store/fs/encrypt/{FileEncrypt,XTS,FilePathEncrypt}`: XTS + `BlockCipher`, `BLOCK_SIZE=4096`, `SALT_LENGTH=8`, `HASH_ITERATIONS=10`; no hay MAC/HMAC en el camino de cifrado |
| H2 deserializa con Java | `org/h2/mvstore/type/ObjectDataType.class` invoca `ObjectInputStream.readObject` |
| El asunto filtra la zona horaria | ejecución real de `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssz")` → `2026-10-06 00:25:13CEST`; `JavaTime.YYYY_MM_DD_HH_MM_SSz` es exactamente ese patrón |
| `Lettera.stripAnsi` sin usar | grep en `app/src`: sólo la definición (`Lettera.java:271`) |
| Sin gestión de permisos de fichero | grep de `setReadable|setWritable|setPosixFilePermissions|chmod|PosixFilePermission` en `app/src/main/java`: 0 coincidencias |
| Sin inyección de comandos en `pass-path` | `PASS.getKey` → `Runtime.getRuntime().exec(new String[]{"pass","show",name})` |
| Cifrado GPG sin armor en `MailPush` | `GPG.armor` es `volatile boolean` por defecto `false`; `Lettera.java:66` y `HubBot.java:23` lo activan, `MailPush.java:24` **no** |
| Dependencias ZXing/qrcodegen sin usar | grep de `zxing\|qrcodegen\|QRCode` en `app/src`: 0 coincidencias (están en `app/build.gradle`) |
| Log de fichero en INFO | `Log.WARN=3`, `Log.INFO=2`; `fileLevel = max(consoleLevel-1, 0)` → 2 |

---

## 9. Plan de remediación (por prioridad)

### P0 — antes de declarar que el chat funciona

1. **Verificar firmante y firma al recibir** (A1): `DecryptStatus` obligatorio,
   `status.getSigner()` == `Friend.fingerprint`, rechazar el resto. Una sola función
   `verify(SealedNote, Friend) : Optional<PlainNote>` usada por **todos** los caminos
   de recepción.
2. **Cifrar a la huella, no al email** (A2): `--recipient <fingerprint>`,
   `--assert-signer`; `fingerprint` obligatorio para enviar/recibir.
3. **TLS verificado** (A3): `checkserveridentity=true` para SMTP e IMAP,
   `starttls.required=true` cuando `auth`, y error duro si no hay TLS. Arreglar los
   argumentos cruzados de `MailPoll` y añadir STARTTLS a `IMAP` (B1, B2).
4. **Sin armor corrupto** (A4): `new GPG().setArmor(true)` en `MailPush` y enviar
   bytes, no `new String(binario, UTF8)`.
5. **Filtrar antes de descifrar** (E1, E2): remitente esperado, `maxBytes`,
   rango de mensajes por UID (no `getMessages()` completo), y filtro de asunto.
6. **Permisos de fichero** (C3): `0700` el directorio, `0600` los ficheros y el log.
7. **Arreglar `--tor`** (B3) y **no enviar sin proxy disponible**.
8. **Asunto opaco sin metadatos** (D1, F4): sin huellas ni zona horaria; `gpgKeyId`
   corto o ausente.

### P1 — antes de un release público

9. **Sanear la salida del terminal** (D2): usar `Lettera.stripAnsi` en todo lo que
   venga de la red; no imprimir HTML.
10. **El bus no devuelve secretos** (C2): `CryptoActor` sin `UnwrapResp` con
    `char[]`; `replyTo` con interfaz propia registrada.
11. **Envolver los secretos de sesión** (F2) y limpiar los `char[]` intermedios (H2).
12. **Id de nota opaco y no reutilizado como clave remota** (F3).
13. **Cache del keyring corta o invalidable** (G2); registrar los fallos de los
    actores (G4); cierre único y ordenado con el hub dentro (G5).
14. **Validar `--username`** y unificar la clave de chat con la de amigo (G6, G7).
15. **KDF y su configuración** (G1): coste acotado en código, `.properties` con
    permisos correctos, Rechazar passphrasesdictionario-obvias.
16. **`--passphrase` como modo de riesgo explícito**, documentado; camino principal
    `--pass-path`/stdin (C4).
17. **Límites**: `maxBytes`, máximo de mensajes por ciclo, backoff con techo (G3);
    buzón en `READ_ONLY` (H4).

### P2 — robustez a medio plazo

18. **Autenticación de la BD**: capa AEAD sobre el MVStore y sin
    `ObjectDataType`; o migrar a un almacén cifrado+autenticado propio (C1).
19. **Rotación de claves y cambio de passphrase** (H3), con re-envoltura de
    secretos (`SecureWrapper` ya lo facilita).
20. **Verificación de huellas fuera de banda**: usar ZXing/qrcodegen (ya en
    `build.gradle`) para QR de verificación y/o SKIP/JPAKE (ya en `nut`:
    `SKIP.replyChallenge`) para autenticar la sesión → subir
    `trustLevel` a `VERIFIED_JPAKE` (F1).
21. **Sin secreto del futuro**: hoy cada nota se cifra directamente a la clave
    estática del destinatario, así que una clave comprometida descifra todo el
    histórico recibido; considerar sesiones efímeras / claves por conversación.
22. **Preparar la GUI web** (H5): cualquier puente Java↔JS es una superficie de
    ejecución: garantizar que sólo expose operaciones con autorización y sin
    escritura de secretos.