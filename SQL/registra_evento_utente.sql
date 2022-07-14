create or replace PROCEDURE registra_evento_utente (
/*
 * Registra la riga relativa all'evento di login, errore di login 
 * o reset/cambio password, eliminando se necessario le vecchie registrazioni
 * di LOGIN_OK per le connessioni da ws (sono troppe ed inutili)
 *
 * author: ff
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
    tmp_conta   NUMBER;
BEGIN
-- Gli eventi da registrare sono:
-- 'BAD_PASS', 'BAD_USER', 'EXPIRED', 'LOCKED' errori, da conservare
-- 'LOGIN_OK', positivo, vanno eliminati i dati vecchi per gli automi 
-- 'SET_PSW', neutro, va conservato
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

    -- dbms_output.put_line('scritto riga');

-- verifico se è LOGIN_OK ed è un automa

    SELECT COUNT(*)
    INTO
        tmp_conta
    FROM usr_user t
    WHERE t.nm_userid = p_nm_userid
        AND t.tipo_user = 'AUTOMA';

    IF
        tmp_conta = 1 AND p_tipo_evento = 'LOGIN_OK'
    THEN
    -- conto se ci sono analoghe registrazioni
    -- più vecchie delle ore 00 di oggi
    -- e/o della data della riga che si vuole inserire,
    -- per evitare di cancellare quanto appena inserito
    -- nel caso la data sia nel passato, cosa possibile
    -- se i clock del appserver e del db server sono
    -- desincronizzati e questa stored viene chiamata alle 23.59
        -- dbms_output.put_line('LOGIN_OK ed automa');
        --
        SELECT COUNT(*)
        INTO
            tmp_conta
        FROM sacer_log.log_evento_login_user t
        WHERE t.dt_evento < trunc(SYSDATE)
            AND t.dt_evento < p_dt_evento
            AND t.nm_userid = p_nm_userid
            AND t.tipo_evento = 'LOGIN_OK';

        IF
            tmp_conta > 0
        THEN
        -- dbms_output.put_line('tmp_conta ' || tmp_conta);
        -- se ci sono le elimino.
        -- qusto controllo riduce il rischio di inserire un lock sulla
        -- tabella a causa della delete se non è strettamente necessario
            DELETE FROM sacer_log.log_evento_login_user t WHERE t.dt_evento < trunc(SYSDATE)
                AND t.dt_evento < p_dt_evento
                AND t.nm_userid = p_nm_userid
                AND t.tipo_evento = 'LOGIN_OK';

        END IF;

    END IF;

    -- dbms_output.put_line('finito');

END;