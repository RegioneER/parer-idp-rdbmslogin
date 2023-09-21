# IDP RDBMS Login

Fonte template redazione documento:  https://www.makeareadme.com/.


# Descrizione

Il seguente progetto è utilizzato come **dipendenza** interna.
Lo scopo è quello di implementare logiche condivise a più applicazioni, che definisco gli standard di tracciabilità di eventi e verifica della validità delle credenziali degli operatori che per mezzo di piattaforme SSO (Single Sign On) effettuano il login nei singoli contesti applicativi.

# Installazione

Come già specificato nel paragrafo precedente [Descrizione](# Descrizione) si tratta di un progetto di tipo "libreria", quindi un modulo applicativo utilizzato attraverso la definzione della dipendenza Maven secondo lo standard previsto (https://maven.apache.org/): 

```xml
<dependency>
    <groupId>it.eng.parer</groupId>
    <artifactId>idp-jaas-rdbms</artifactId>
    <version>$VERSIONE</version>
</dependency>
```

# Utilizzo

Il modulo implementa al suo interno le logiche standard attraverso le quali si attuano verifiche di validità dell'operatore che effettua il login su singoli contesti applicativi attraverso gli appositi portali SSO (Single Sign On) predisposti, ed inoltre, sono previste azioni di tracciabilità di tali eventi su base dati. Ogni applicazione su cui è presente tale dipendenza, effettuerà le opportune chiamate ai metodi a disposizione nelle fasi di login / logout previste, sono inoltre previste delle apposite procedure (vedi [SQL](SQL)) con le quali si attuano alcune azioni tra cui ad esempio la disattivazione di uno specifico utente.

# Supporto

Progetto a cura di [Engineering Ingegneria Informatica S.p.A.](https://www.eng.it/).

# Contributi

Se interessati a crontribuire alla crescita del progetto potete scrivere all'indirizzo email <a href="mailto:areasviluppoparer@regione.emilia-romagna.it">areasviluppoparer@regione.emilia-romagna.it</a>.

# Autori

Proprietà intellettuale del progetto di [Regione Emilia-Romagna](https://www.regione.emilia-romagna.it/) e [Polo Archivisitico](https://poloarchivistico.regione.emilia-romagna.it/).

# Licenza

Questo progetto è rilasciato sotto licenza GNU Affero General Public License v3.0 or later ([LICENSE.txt](LICENSE.txt)).
