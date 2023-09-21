create or replace PROCEDURE registra_evento_utente (
/*
 * Registra la riga relativa all'evento di login, errore di login 
 * o reset/cambio password, eliminando se necessario le vecchie registrazioni
 * per le connessioni da ws (sono troppe ed inutili).
 *
 * author: ff
 * note:
 * 2018-02-01 aggiunto il lock esclusivo prima delle operazioni di delete
 *            per evitare problemi di concorrenza/deadlock con le chiamate
 *            provenienti da ws
 * ex:
 begin 
    REGISTRA_EVENTO_UTENTE(
    'T800.Bonnienator',
    '192.168.0.256',
    '127.0.0.256',
    'BAD_PASS',
    'il fallimento',
    systimestamp
  );
 end;
 
 begin 
    REGISTRA_EVENTO_UTENTE(
    'admin_generale',
    '192.168.0.256',
    '127.0.0.256',
    'LOGIN_OK',
    'Login avvenuto con successo',
    systimestamp
  );
 end;
 
 */
    p_nm_userid       IN VARCHAR2, -- l'utente di cui si vuole salvare l'evento
    p_cd_ind_client   IN VARCHAR2, -- l'indirizzo del client, se presente
    p_cd_ind_server   IN VARCHAR2, -- l'indirizzo o il nome del server che processa il dato
    p_tipo_evento     IN VARCHAR2, -- il tipo evento da memorizzare
    p_ds_evento       IN VARCHAR2, -- la descrizione dell'evento
    p_dt_evento       IN TIMESTAMP -- il timestamp dell'evento da scrivere
) IS
    tmp_conta       NUMBER;
    tmp_is_automa   NUMBER;
    tmp_usr_user    usr_user%rowtype;
BEGIN
-- Gli eventi da registrare sono:
-- 'BAD_PASS', 'BAD_USER', 'EXPIRED', 'LOCKED' errori
-- 'LOGIN_OK', positivo
--  di questi eventi vanno eliminati i dati vecchi per gli automi 
-- 'SET_PSW', neutro, va conservato



-- verifico se è un automa
    SELECT CASE
            WHEN EXISTS (
                SELECT NULL
                FROM usr_user t
                WHERE t.nm_userid = p_nm_userid
                    AND t.tipo_user = 'AUTOMA'
            ) THEN 1
            ELSE 0
        END
    INTO
        tmp_is_automa
    FROM dual;

    --   dbms_output.put_line('automa? '||tmp_is_automa);

-- verifico se oltre ad essere un automa, sta registrando un LOGIN_OK

    IF
        tmp_is_automa = 1 AND p_tipo_evento = 'LOGIN_OK'
    THEN
    -- verifico se ci sono registrazioni
    -- più vecchie delle ore 00 di oggi
    -- e/o del timestamp della riga che si vuole inserire,
    -- per evitare incoerenze nel caso la data
    -- sia nel passato; la cosa è possibile
    -- se i clock del appserver e del db server sono
    -- desincronizzati e questa stored viene chiamata alle 23.59
    --
    -- questo controllo riduce il rischio di avere lock sulla
    -- tabella a causa della delete se non è strettamente necessario
    --
    --   dbms_output.put_line('LOGIN_OK ed automa');
    --
        SELECT CASE
                WHEN EXISTS (
                    SELECT NULL
                    FROM sacer_log.log_evento_login_user t
                    WHERE t.dt_evento < trunc(SYSDATE)
                        AND t.dt_evento < p_dt_evento
                        AND t.nm_userid = p_nm_userid
                        AND t.tipo_evento IN (
                                'LOGIN_OK', 'BAD_PASS', 'BAD_USER', 'EXPIRED', 'LOCKED'
                            )
                ) THEN 1
                ELSE 0
            END
        INTO
            tmp_conta
        FROM dual;
        
        --   dbms_output.put_line('vecchi log tmp_conta? '||tmp_conta);

        IF
            tmp_conta > 0
        THEN
        -- se ci sono le elimino:
        -- 
        -- blocco il record di usr_user, devo impedire che questa
        -- procedura possa essere eseguita da più client simultaneamente
        -- (questo è normale in un contesto WS)
            SELECT *
                INTO
                    tmp_usr_user
                FROM usr_user t
                WHERE t.nm_userid = p_nm_userid
            FOR UPDATE WAIT 35;
        -- cancello i record
            DELETE FROM sacer_log.log_evento_login_user t WHERE t.dt_evento < trunc(SYSDATE)
                AND t.dt_evento < p_dt_evento
                AND t.nm_userid = p_nm_userid
                AND t.tipo_evento IN (
                        'LOGIN_OK', 'BAD_PASS', 'BAD_USER', 'EXPIRED', 'LOCKED'
                    );

        END IF;

    END IF;
    
-- verifico se è SET_PSW ed è un automa

    IF
        tmp_is_automa = 1 AND p_tipo_evento = 'SET_PSW'
    THEN
    
    --   dbms_output.put_line('SET_PSW ed automa');
    
    -- elimino tutte le registrazioni del ws precedenti il
    -- timestamp della riga da inserire.
    -- in questo caso non dovrei avere problemi di concorrenza poiché
    -- questa funzione è attivabile solo online e non da ws.
    --
    -- cancello i record
        DELETE FROM sacer_log.log_evento_login_user t WHERE t.dt_evento < p_dt_evento
            AND t.nm_userid = p_nm_userid
            AND t.tipo_evento IN (
                    'LOGIN_OK', 'BAD_PASS', 'BAD_USER', 'EXPIRED', 'LOCKED'
                );

    END IF;
    

-- registro l'evento, finalmente

    INSERT INTO sacer_log.log_evento_login_user (
        id_evento_login_user,
        nm_userid,
        cd_ind_ip_client,
        cd_ind_server,
        dt_evento,
        tipo_evento,
        ds_evento
    ) VALUES (
        to_number(1000 || TO_CHAR(sacer_log.slog_evento_login_user.nextval) ),
        p_nm_userid,
        p_cd_ind_client,
        p_cd_ind_server,
        p_dt_evento,
        p_tipo_evento,
        p_ds_evento
    );
    
    --   dbms_output.put_line('scritto la riga');

END;