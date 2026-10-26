# PocketLive USB — Android (ASAPGRINGOO)

## Aggiornamento 0.2.0

- Risoluzioni d'uscita aggiuntive: 3200×1800, **3K 3072×1728**, 2880×1620, 2704×1520 e 2688×1512, visibili solo se l'encoder le dichiara compatibili.
- Al collegamento USB la risoluzione d'ingresso viene scelta in funzione dell'uscita selezionata: una live 1080p non richiede automaticamente 4K dalla DJI.
- Il render dell'anteprima è limitato a circa 1080p per alleggerire GPU e batteria durante la live, mantenendo separata la risoluzione di codifica.
- Quando cambia la risoluzione UVC, la superficie di acquisizione viene ricreata con le dimensioni effettive della fotocamera, riducendo ridimensionamenti non necessari.
- Avvio anteprima USB protetto contro ripetute richieste di `startPreview()`.
- Se in `USB Camera Test → Video Format` scegli manualmente una modalità, la selezione manuale viene conservata.

Il 3K è una **risoluzione del video codificato**, non una nuova modalità USB inventata per la DJI. Un flusso 4K30 continuo non può essere garantito senza misurare frame persi, capacità encoder e temperatura sul Samsung S9. Se il 4K scatta, provare prima 3072×1728 a 30 fps, poi 2560×1440 a 30 fps. Il progetto completo Moblin per Android è un lavoro separato.

App Android sperimentale per DJI Osmo Pocket 4 Pro -> USB OTG -> Android -> Moblin (iPhone) tramite RTMP/SRT. **Gratis, nessun abbonamento.**

È una personalizzazione di [LifeStreamer](https://github.com/dimadesu/LifeStreamer), distribuita con gli stessi termini GPL-3.0. Non è un prodotto ufficiale DJI o Moblin. Codice upstream e copyright originali restano attribuiti ai rispettivi autori.

## Cosa cambia rispetto a LifeStreamer

- Nome e identificatore app separati: `PocketLive USB`, `it.asapgringoo.pocketliveusb` (installabile accanto a LifeStreamer).
- La sorgente UVC cerca il formato nativo MJPEG più adatto alla risoluzione di uscita, fino a 3840x2160 a 30 fps, **solo tra quelli realmente dichiarati dalla videocamera**.
- Rimane possibile cambiare manualmente il formato: menu USB source -> Video Format.
- La lista delle risoluzioni di uscita non è filtrata sulle fotocamere *interne* per impostazione predefinita. Il codec hardware può comunque impedire il 4K.
- Controlli bitrate locale fino a 30.000 kbps, con 12.000 kbps predefiniti.
- Si basano su streaming via RTMP, SRT e H.264/H.265 già presenti nel progetto LifeStreamer.

## Utilizzo con Moblin

1. Attiva l'hotspot sull'iPhone con rete dati cellulare, collega il telefono Android a quell'hotspot.
2. Su Moblin avvia il server locale di ingest RTMP già associato alla sorgente `Op4 (RTMP)`; copia esattamente il suo indirizzo RTMP (IP locale e porta).
3. Collega DJI Pocket 4 Pro via USB-C/OTG al telefono Android. Se la camera lo richiede, scegli modalità webcam/UVC.
4. Apri PocketLive USB, concedi accesso USB, attiva `USB SRC` (verde = sorgente attiva). In `USB Camera Test → Video Format` verifica quali formati MJPEG sono presenti; scegli 1920x1080 30 fps per partire, poi eventualmente una modalità superiore.
5. Nelle impostazioni video, seleziona H.264, 30 fps, risoluzione 1920x1080 (test iniziale) e 12.000–16.000 kbps. Non utilizzare 1920x1440 perché è 4:3.
6. Imposta l'endpoint RTMP a quello copiato da Moblin. Non inserire l'URL Twitch sul telefono Android: è Moblin che farà la live finale.
7. Premi `START LIVE` in PocketLive e verifica l'anteprima in Moblin. Poi avvia la live da Moblin con il tuo bitrate finale (8 Mbps).

### 4K30

Se la Pocket 4 Pro dichiara un ingresso UVC **3840x2160 MJPEG a 30 fps**, l'app può tentare di selezionarlo. Il collegamento USB, l'encoding hardware del telefono e l'uscita RTMP sono tre cose distinte. Il codec Android potrebbe offrire solo 1080p, persino quando l'ingresso USB fornisce 4K. Non è possibile forzare 4K utile su un encoder che non lo supporta. Non equivale al file 4K registrato internamente dalla DJI.

Le modifiche richiedono **verifica su dispositivo reale** (Samsung e Pocket 4 Pro); non garantiscono né 4K né stabilità su tutti gli hardware. Non abbiamo eseguito test end-to-end sul dispositivo dell'utente.

## Compilazione

Il progetto richiede Android SDK (API 36), Java 17 e una connessione per le dipendenze Gradle.

```sh
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

I submoduli upstream usano URL SSH, perciò su un runner senza chiavi SSH usare:

```sh
git -c url.https://github.com/.insteadOf=git@github.com: submodule update --init --recursive
```

L'APK di debug firmato automaticamente si trova in `app/build/outputs/apk/debug/` oppure negli artifact GitHub Actions se si usa il workflow incluso.

## Licenze e sicurezza

- LifeStreamer (GPL-3.0): https://github.com/dimadesu/LifeStreamer
- StreamPack e relativi moduli: vedere le licenze upstream.
- Questa variante viene distribuita con il codice sorgente e la licenza upstream. Nessun codice per aggirare paywall o licenze CameraFi.
- Il server RTMP locale non è cifrato: usalo solo sulla tua rete/hotspot privata.
