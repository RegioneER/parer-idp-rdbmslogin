# Informazioni sull'applicazione:

Questo progetto genera, nella cartella deploy, il modulo jboss da aggiungere all'installazione.
La procedura di build è gestita esclusivamente da maven che crea, nella cartella deploy, il pacchetto che contiene il
percorso del modulo jboss.

Le informazioni che erano contenute nel file ***/opt/shibboleth/conf/login.config***, presente nelle configurazioni
dell'idp per glassfish, sono state spostate nel domain.xml (o standalone.xml) del server jboss.

Attuamente su parer-svil c'è la seguente configurazione (nel file ***/opt/jboss-eap/master/configuration/domain.xml***):
```xml
[...]
<security-domain name="ShibUserPassAuth" cache-type="default">
    <authentication>
        <login-module code="com.tagish.auth.DBLogin" flag="required" module="com.tagish.auth">
            <module-option name="userTable" value="USR_USER"/>
            <module-option name="userColumn" value="NM_USERID"/>
            <module-option name="passColumn" value="CD_PSW"/>
            <module-option name="saltColumn" value="CD_SALT"/>
            <module-option name="activeColumn" value="FL_ATTIVO"/>
            <module-option name="expirationColumn" value="DT_SCAD_PSW"/>
            <module-option name="useJndiLookup" value="true"/>
            <module-option name="jndiName" value="java:/jdbc/SiamDs"/>

            <module-option name="qryRegistraEventoUtente" value="begin REGISTRA_EVENTO_UTENTE(:nmUser,:cdIndIpClient,:cdIndServer,:tipoEvento,:dsEvento,:tsEvento);end;" />
            <module-option name="serverNameSystemProperty" value="jboss.node.name" />
            <module-option name="qryRetrieveMaxDays" value="select T.DS_VALORE_PARAM_APPLIC from iam_param_applic t where T.NM_PARAM_APPLIC = 'MAX_GIORNI'" />
            <module-option name="qryRetrieveMaxTry" value="select T.DS_VALORE_PARAM_APPLIC from iam_param_applic t where T.NM_PARAM_APPLIC = 'MAX_TENTATIVI_FALLITI'" />
            <module-option name="qryVerificaDisattivazioneUtente" value="select VERIFICA_DISATTIVAZIONE_UTENTE(:nmUser,:tipoEvento,:maxTentativi,:maxGiorni,:tsEvento) as resp from dual" />
            <module-option name="qryDisableUser" value="begin DISATTIVA_UTENTE(:nmUser, :tsEvento); end;" />
        </login-module>
    </authentication>
</security-domain>
[...]
```
che mappa 1:1 il file ***/opt/shibboleth/conf/login.config***
```java
[...]
ShibUserPassAuth {
 com.tagish.auth.DBLogin required debug="true"
	useJndiLookup="true"		
	  jndiName="java:/jdbc/SiamDs"		
	  userTable="USR_USER"
	  userColumn="NM_USERID"
	  passColumn="CD_PSW"
	  saltColumn="CD_SALT"
	  activeColumn="FL_ATTIVO"
	  expirationColumn="DT_SCAD_PSW"
	  logTable = "SACER_LOG.LOG_LOGIN_FALLITO"
	  logTableCols = "NM_APPLIC, NM_USERID, CD_IND_SERVER, TIPO_FALLIMENTO, CD_IND_IP_CLIENT, DS_FALLIMENTO, DT_FALLIMENTO, ID_LOGIN_FALLITO"
	  logTableValues = ":nmApplic, :nmUser, :cdIndServer, :tipoFallimento, :cdIndIpClient, :dsFallimento, :tsFallimento, SACER_LOG.SLOG_LOGIN_FALLITO.nextval"
	  serverNameSystemProperty = "jboss.node.name";
};
```

**Nota Bene**: la stored procedure ```DISATTIVA_UTENTE``` usata nella query ```qryDisableUser```
è presente nel file ```disattiva_utente.sql```

Una trattazione più approfondita dei parametri utilizzati è disponibile alla seguente pagina della wiki:
https://rersvn.ente.regione.emr.it/projects/parer/wiki/IDP_ConfigDBLogin .

Il tracciamento del modulo (in particolare della classe ```com.tagish.auth.DBLogin.java``` ) avviene tramite
```java.util.logging.Logger``` .
Al momento traccia la query di selezione e le query di insert in caso di errore.
Il livello di tracciamento è ```java.util.logging.Level.FINE```.
