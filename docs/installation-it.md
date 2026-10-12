# Installare Moon Patches

## Fonte Morphe

1. Apri Morphe, poi **Fonti → + → Remota**.
2. Incolla `https://github.com/noire342/moon-patches`, oppure apri [Aggiungi a Morphe](https://morphe.software/add-source?github=noire342/moon-patches&name=Moon%20Patches).
3. Scegli Moon Patches come fonte per YouTube. Il bundle comprende già le patch della base: evita di selezionare contemporaneamente le stesse patch da altre fonti.
4. Usa un APK originale compatibile. Le versioni provate sono riportate nelle note della release e in [validation.md](validation.md).
5. Includi **Nya Rooms**, **Custom branding** e **Change header**, poi ripatcha e installa.

Se Morphe non prosegue dopo la scelta della fonte quando apri direttamente un APK, avvia la patch dalla scheda YouTube nella Home del Patcher, scegli Moon e usa l’APK originale salvato. Questo percorso è stato verificato sul dispositivo.

Per l'importazione locale puoi scaricare `patches-<versione>.mpp` dalla release e scegliere **Fonti → + → Locale**. La fonte remota permette gli aggiornamenti dal repository. Ogni release include un file SHA256: il checksum verifica l'integrità, non è una firma crittografica dell'autore.

## Aspetto Moon

Con Custom branding seleziona l'icona **Moon** e il nome **Moon**. Durante la patch puoi anche impostare `appIcon=moon` e `customName=Moon`. Nelle impostazioni dell'app, in **Change header**, seleziona **Moon**: la scritta usa il gradiente giallo, fuchsia e celeste, con la luna sulla destra. Il tema chiaro e quello scuro hanno colori adattati alla leggibilità.

I vecchi alias delle icone rimangono validi per gli aggiornamenti, ma usano grafica Moon. Le impostazioni conservano la compatibilità con la base Morphe.

## Guardare insieme

Il pulsante **Guarda insieme** è nella barra superiore accanto a Cerca e nei controlli del player.

1. Inserisci un soprannome e crea la room, anche dalla Home.
2. Condividi o copia il codice di otto cifre. L'ospite lo incolla in **Entra**; puoi incollare anche l'intero messaggio d'invito.
3. L'host controlla il soprannome e l'impronta della richiesta, poi approva l'accesso. Confronta l'impronta con l'altra persona attraverso un canale fidato.
4. Scegli un video. Gli ospiti lo aprono automaticamente nell'app.
5. Tutti possono mettere in pausa, riprendere e spostarsi nel video, anche dai normali controlli YouTube. L'host può disattivare i controlli condivisi nelle opzioni.

Chiudere il pannello con × o trascinandolo lascia aperta la room. **Esci** abbandona la sessione; **Chiudi room** la termina per tutti. SponsorBlock rimane sospeso per l'intera sessione, compresi ingresso e riconnessione. Le impostazioni SponsorBlock vengono conservate.

## Problemi frequenti

| Problema | Azione |
| --- | --- |
| Nessun pulsante room | Verifica che Nya Rooms fosse inclusa nella patch e riavvia l'app |
| Connessione lenta | Controlla la rete; i relay pubblici possono essere temporaneamente indisponibili |
| Attesa di approvazione | L'host deve aprire il pannello e approvare la richiesta |
| Video diverso o non disponibile | Verifica le limitazioni dell'account, regione e rete di ciascun telefono |
| Shorts, diretta o cast | Usa un normale video nel player del telefono |
| Fonte senza metadati | Aggiorna Morphe e verifica che il repository e la release siano raggiungibili |

Segnala i problemi nel [repository Moon](https://github.com/noire342/moon-patches/issues), indicando versione del bundle, Morphe, Android e YouTube. Non pubblicare codici di room, inviti completi, chiavi, token, cookie o dati dell'account.
