create or replace FUNCTION verifica_disattivazione_utente (
/**
Verifica se l'evento che si sta per inserire determinerà la 
disattivazione dell'utente
Per attivare la verifica, il tipo evento deve essere BAD_PASS altrimenti
la risposta sarà sempre negativa.

Esiti possibili:

NO : (messaggio eventuale)
   - se p_ti_fallimento è diverso da BAD_PASS
   - se la funzione di disabilitazione è disattivata
     (parametro applicativo DISATTIVA_UTENTI <> true)
   - se p_nm_userid non esiste
   - se p_nm_userid è disattivato o scaduto
   - se il numero di BAD_PASS dalla data iniziale è minore di p_ni_max_tentativi
   

DISATTIVARE - se l'utente esiste ed è attivo
              e si sta per registrare un evento di BAD_PASS
              e se dal più recente tra:
                 * dall'ultimo login ok 
                 * dall'ultimo reset password
                 * da (data p_dt_evento - p_ni_max_giorni)
              si sono verificati almeno p_ni_max_tentativi errati.

author: ff

Esempi:
select VERIFICA_DISATTIVAZIONE_UTENTE(
    'consultatore',
    'BAD_PASS',
    3,
    30,
    to_date('17-OTT-2017 10:15:14','DD-MON-YYYY HH24:MI:SS')
  ) as resp from dual;
  
select VERIFICA_DISATTIVAZIONE_UTENTE(
    'admin_generale',
    'BAD_PASS',
    3,
    30,
    systimestamp
  ) as resp from dual;

*/
    p_nm_userid          IN VARCHAR2, -- l'utente di cui si vuole salvare l'evento
    p_tipo_evento        IN VARCHAR2, -- il tipo evento da memorizzare
    p_ni_max_tentativi   IN NUMBER, -- il massimo numero di tentativi fallibili
    p_ni_max_giorni      IN NUMBER, -- quanto giorni ricercare nel passato
    p_dt_evento          IN TIMESTAMP -- il timestamp dell'evento da scrivere
) RETURN VARCHAR2 AS

    tmp_dt_evento     sacer_log.log_evento_login_user.dt_evento%TYPE;
    tmp_data_inizio   TIMESTAMP;
    tmp_conta         NUMBER;
    tmp_risposta      VARCHAR2(50);
BEGIN
    tmp_risposta      := 'NO';
    /*
    Verifico se la riga che si intende scrivere è coinvolta
    nel calcolo della disattivazione utente.
    I tipi fallimento sono BAD_PASS,LOCKED,EXPIRED,BAD_USER
    ma a noi interessa solo BAD_PASS,che è quello che provoca 
    la disattivazione dell'utente dopo 'p_ni_max_tentativi' tentativi errati
    nell'arco di 'p_ni_max_giorni' giorni
    */
    IF
        p_tipo_evento <> 'BAD_PASS'
    THEN
        -- dbms_output.put_line('p_tipo_evento ' || p_tipo_evento);
        tmp_risposta   := 'NO : evento diverso da BAD_:PASS';
        RETURN tmp_risposta;
    END IF;
    
    /*
    verifico se devo disattivare l'utente a seguito di n
    login falliti. Nel caso del sistema installato in Puglia
    che non usa il nostro IDP la disabilitazione non ha senso
    dal momento che gli utenti vengono verificati su un altro
    database (LDAP). Inquesto caso rendo sempre ed in ogni
    caso una risposta negativa.
    */
    SELECT COUNT(*)
    INTO
        tmp_conta
    FROM iam_param_applic t
    WHERE t.nm_param_applic = 'DISATTIVA_UTENTI'
        AND t.ds_valore_param_applic = 'true';

    IF
        tmp_conta <> 1
    THEN
        -- dbms_output.put_line('La funzione di disattivazione non è attiva');
        tmp_risposta   := 'NO : funzione di disattivazione disabilitata';
        RETURN tmp_risposta;
    END IF;
    
    /*
    verifico se esiste l'utente, ed è anche attivo
    altrimenti in ogni caso la disattivazione
    non ha senso
    */
    SELECT COUNT(*)
    INTO
        tmp_conta
    FROM usr_user t
    WHERE t.nm_userid = p_nm_userid
        AND t.fl_attivo = 1;

    IF
        tmp_conta <> 1
    THEN
        -- dbms_output.put_line('Utente non trovato o non attivo');
        tmp_risposta   := 'NO : utente non trovato o non attivo';
        RETURN tmp_risposta;
    END IF;
    
    
    
    /*
    calcolo la data da cui iniziare a contare i login errati,
    prima sottraendo dalla data attuale i giorni passati come parametro
    */
    tmp_data_inizio   := p_dt_evento - numtodsinterval(p_ni_max_giorni, 'DAY');
    -- dbms_output.put_line('tmp_data_inizio ' || tmp_data_inizio);
    /*       
    calcolo l'ultimo login corretto negli ultimi p_ni_max_giorni giorni,
    se lo trovo, lo sostituisco alla data inizio
    */
    SELECT MAX(t.dt_evento)
    INTO
        tmp_dt_evento
    FROM sacer_log.log_evento_login_user t
    WHERE t.nm_userid = p_nm_userid
        AND t.dt_evento > tmp_data_inizio
        AND t.dt_evento <= p_dt_evento
        AND ( t.tipo_evento = 'LOGIN_OK'
            OR t.tipo_evento = 'SET_PSW'
        );

    IF tmp_dt_evento IS NOT NULL
    THEN
        tmp_data_inizio   := tmp_dt_evento;
        -- dbms_output.put_line('tmp_data_inizio corretta ' || tmp_data_inizio);
    END IF;
    /*
    conto quanti BAD_PASSWORD si sono verificati
    a partire dalla data inizio periodo calcolata
    (ultimi n giorni o ultimo login buono)
    */
    SELECT COUNT(*)
    INTO
        tmp_conta
    FROM sacer_log.log_evento_login_user t
    WHERE t.nm_userid = p_nm_userid
        AND t.dt_evento > tmp_data_inizio
        AND t.dt_evento <= p_dt_evento
        AND t.tipo_evento = 'BAD_PASS';

    -- dbms_output.put_line('tmp_conta ' || tmp_conta);          

    tmp_risposta      := 'NO : numero fallimenti = ' || tmp_conta;

    /*
    se il numero di errori BAD_PASSWORD è uguale o supera il
    limite consentito, allora decreto la disattivazione
    senza alcuna pietà per l'utente
    */
    IF
        tmp_conta >= p_ni_max_tentativi
    THEN
        tmp_risposta   := 'DISATTIVARE';
    END IF;
    RETURN tmp_risposta;
END;