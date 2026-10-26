# Moblin Android — port non ufficiale (alpha, ricostruzione progressiva)

Questa directory avvia il porting Android del progetto originale Moblin, i cui sorgenti iOS si trovano nelle directory `../Moblin` e `../Common`. Il codice Swift/SwiftUI di iOS non è eseguibile su Android. Il backend iniziale Android usa il codice aperto di LifeStreamer, StreamPack, UVCAndroid e bond-bunny, integrato con una nuova schermata Android ispirata ai controlli principali di Moblin. Non è un rilascio ufficiale di Moblin.

## Aggiornamento interfaccia alpha 0.2.0

- Nuova schermata live a tutto schermo con pannelli fluttuanti per la selezione Scene, Chat, DJI USB, Strumenti e Impostazioni.
- Stato della trasmissione indicato in alto con colori diversi per LIVE, errore, connessione e offline, collegato allo stato del motore Android.
- Indicatore della sorgente selezionata DJI USB / telefono.
- Menu Strumenti per aprire e richiudere i vecchi comandi avanzati senza che occupino stabilmente lo schermo.
- La chat Twitch si apre come riquadro sopra l'anteprima, richiamabile e richiudibile durante l'uso; toccando il suo titolo si apre anche il pannello espanso. Rimane un WebView locale, non una chat nativa né un widget codificato nel video trasmesso.
- Compilazione APK proposta tramite GitHub Actions in `.github/workflows/build-android.yml`, quando `Android/` e `.github/` sono nella radice di un repository GitHub.

L'interfaccia è una ricostruzione della disposizione di Moblin, non è ancora identica pixel per pixel alla UI SwiftUI iOS e non include ancora tutte le funzionalità originarie. Nessuna versione APK 0.2.0 è stata verificata finché la compilazione automatica non termina correttamente.

## Funzioni realmente presenti nella prima alpha

- Schermata live Android con anteprima, pulsante START/STOP, bitrate e stato dello stream.
- Trasmissione RTMP/RTMPS e SRT mediante il backend Android esistente, con relativa schermata impostazioni.
- Selezione delle scene base: videocamera del telefono oppure DJI Pocket tramite USB UVC. Cambia la sorgente, non compone ancora widget sulla trasmissione.
- Configurazione della sorgente USB ed elenco dei formati annunciati dalla fotocamera.
- Scelte output 1080p, 2K/QHD 2560×1440, 3K 3072×1728, 4K UHD 3840×2160, se l'encoder segnala il supporto; scelta del formato USB indipendente dalla risoluzione trasmessa.
- Indicazione degli FPS visualizzati nella schermata di test USB, con risoluzione, FPS richiesti dal dispositivo e formato MJPEG/YUV, per isolare i problemi di fluidità.
- Schermata chat Twitch tramite pagina pop-out nel WebView. Non è ancora la chat nativa di Moblin e non è sovrapposta al video trasmesso.
- Impostazioni di base video/audio già disponibili nel backend, come bitrate, codec e frame rate.

## Ancora da portare dall'app Moblin iOS

- Compositore delle scene con widget browser, chat ed alert incorporati nel video finale.
- Chat nativa con autenticazione/moderazione/emote avanzate di Twitch/Kick/YouTube.
- Controllo integrato della DJI via Bluetooth e video wireless nativo.
- Registrazione/multistream, server RTMP, Moblink, remote control e recupero completo delle configurazioni iOS.
- Bonding avanzato e funzione parity per tutti i protocolli, overlay grafici, filtri e strumenti di Moblin iOS.
- Test estesi di affidabilità e performance su Samsung S9 con Pocket 4 Pro.

## Verifica consigliata per DJI + Samsung S9

1. Aprire **DJI USB** → **Test formato USB + FPS reali** prima di avviare la live. Scegliere 1920×1080 MJPEG@30, poi 2560×1440 se esiste, infine 3840×2160 MJPEG@30 se la DJI lo pubblica.
2. Osservare il contatore di FPS **reali** per almeno 10 secondi e ripetere con movimento. Se a 4K scende sotto 25 già qui, il problema è nel collegamento/cattura/decodifica USB e non in Moblin o in RTMP.
3. Chiudere il test USB, riattivare DJI come sorgente in **Scene**, selezionare bitrate e risoluzione nelle impostazioni e verificare gli FPS dell'encoder durante la live.
4. Se la preview USB resta 30 FPS ma la live no, è probabile un limite di composizione o encoding dello S9. Provare prima 2560×1440 a 30 FPS con H.264, poi 1920×1080 a 30 FPS; un'impostazione 2K non garantisce che la modalità acquisita dalla fotocamera sia 2K nativa.
5. Se gli FPS locali sono corretti ma la live ricevuta scatta, verificare la rete mobile e il doppio passaggio Samsung→hotspot iPhone→Moblin/Twitch.

Il supporto dichiarato dalle API MediaCodec è un limite di compatibilità, non una garanzia di performance con fotocamera USB, preview e streaming simultanei. L'hardware reale deve essere provato prima di dichiarare fluido il 4K.

## Costruzione

Aprire `Android/` come progetto Android Studio oppure eseguire `./gradlew :app:assembleDebug` da questa cartella con SDK Android e JDK disponibili. Il package applicativo è `it.asapgringoo.moblinandroid`, distinto dalla beta PocketLive USB.

## Licenze e provenienza

Il progetto Moblin iOS è sotto licenza MIT (vedere `../LICENSE`). Il motore Android iniziale deriva da LifeStreamer (GPLv3, `LICENSE.txt`), StreamPack, UVCAndroid e bond-bunny. Le licenze dei componenti devono essere rispettate se la build viene distribuita; il nuovo port non è automaticamente MIT perché il repository iOS lo è.
# v0.2.1-alpha — UVC frame-rate mode selection

The UVC automatic mode selector now prioritizes camera-advertised 30 FPS modes
before resolution. A 4K mode advertised at 15 FPS will not automatically win
over a 1080p 30 FPS mode merely because of its pixel count. This affects
automatic selection only; the user can still explicitly select any advertised
mode in the USB video-format dialog.

This change **does not** prove 4K30 delivery on a Galaxy S9. The preview FPS
counter measures displayed frames, not USB frame arrival or encoder output.
Further device-side tests must record supported MJPEG/YUYV modes, negotiated
USB bus speed, decode time, and encoding FPS separately.

