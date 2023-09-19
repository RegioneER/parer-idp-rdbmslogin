create or replace PROCEDURE disattiva_utente (
/*
 * disattiva l'utente definito da nmuserid, 
 * aggiorna il log degli eventi,
 * salva i vecchi dati delle password e salt
 * predispone la replicazione dell'utente sugli schema collegati

 Versione 2.0

 usando come riferimento temporale il timestamp ricevuto.
 
 NOTA: questa procedura non effettua il commit
 e può essere parte di una transazione
 
 NOTA2: questa procedura effetua un lock sul record
 di USR_USER relativo all'utente che verrà disattivato
 per evitare problemi di concorrenza:
 se la SP viene invocata simultaneamente più
 volte per lo stesso utente, la disattivazione viene effettuata
 una volta sola, le successive invocazioni eseguite in serie
 rileveranno un utente disattivato e non faranno nulla.
 
 author: ff
 revisione: MI - MEV#24245 - rimozione della disattivazione automatica dell'utente al verificarsi di eventi di sicurezza sull'account username/password sacer
 
 ex: begin DISATTIVA_UTENTE('user.name',systimestamp); end;
 */
    p_nm_userid   IN VARCHAR, -- l'utente da disattivare
    p_dt_evento   IN TIMESTAMP -- il timestamp della disattivazione
) IS

    tmp_usr_user        usr_user%rowtype;
    tmp_id_user_iam     usr_user.id_user_iam%TYPE;
    tmp_vecchio_salt    usr_user.cd_salt%TYPE;
    tmp_vecchia_psw     usr_user.cd_psw%TYPE;
    tmp_adesso          TIMESTAMP;
    tmp_conta           NUMBER;
BEGIN
    tmp_adesso          := systimestamp;
    --
    -- blocco il record di usr_user, devo impedire che questa
    -- procedura possa essere eseguita da più client simultaneamente
    -- (può avvenire se invocata in un contesto WS)
    SELECT *
        INTO
            tmp_usr_user
        FROM usr_user t
        WHERE t.nm_userid = p_nm_userid
    FOR UPDATE WAIT 35;

    -- eseguo la disattivazione solo se l'utente è attivo.
    IF (tmp_usr_user.fl_attivo = 1)
    THEN
        tmp_id_user_iam := tmp_usr_user.id_user_iam;
        tmp_vecchia_psw := tmp_usr_user.cd_psw;
        tmp_vecchio_salt := tmp_usr_user.cd_salt;
        /*
        verifico se devo loggare l'evento di disattivazione:
        è possibile che il logging applicativo sia disattivato.
        */
        -- dbms_output.put_line('verifico il parametro ');
        SELECT COUNT(*)
        INTO
            tmp_conta
        FROM iam_v_getval_param_by_apl t
        WHERE t.nm_param_applic = 'LOG_ATTIVO'
            AND t.ds_valore_param_applic = 'true';
        IF
            tmp_conta > 0
        THEN
            -- dbms_output.put_line('scrivo la riga in log_evento_by_script ');
        -- inserisce una riga nel log degli eventi, usata dal job di registrazione
        -- dei log
            INSERT INTO sacer_log.log_evento_by_script (
                id_evento_by_script,
                id_tipo_oggetto,
                id_oggetto,
                dt_reg_evento,
                id_agente,
                id_azione_comp_sw,
                ti_ruolo_oggetto_evento,
                ti_ruolo_agente_evento,
                id_applic,
                ds_motivo_script
            ) SELECT to_number(1000 ||TO_CHAR(sacer_log.slog_evento_by_script.nextval) ),
                   config.id_tipo_oggetto,
                   usr.id_user_iam id_oggetto,
                   p_dt_evento,
                   config.id_agente,
                   config.id_azione_comp_sw,
                   'outcome',
                   'executing program',
                   config.id_applic,
                   'Reset password per i troppi tentativi di login falliti'
            FROM usr_user usr,
                 (
                    SELECT ti_ogg.id_tipo_oggetto,
                           comp_sw.id_agente,
                           azio_sw.id_azione_comp_sw,
                           apl.id_applic
                    FROM sacer_iam.apl_applic apl
                        JOIN
                             sacer_iam.apl_comp_sw comp_sw
                        ON ( comp_sw.id_applic = apl.id_applic
                        )
                        JOIN
                             sacer_iam.apl_azione_comp_sw azio_sw
                        ON ( azio_sw.id_comp_sw = comp_sw.id_comp_sw
                        )
                        JOIN
                             sacer_iam.apl_tipo_oggetto ti_ogg
                        ON ( ti_ogg.id_applic = apl.id_applic
                        )
                    WHERE apl.nm_applic = 'SACER_IAM'
                        AND comp_sw.nm_comp_sw = 'Controlla login falliti'
                        AND azio_sw.nm_azione_comp_sw = 'Reset password per login falliti'
                        AND ti_ogg.nm_tipo_oggetto = 'Utente'
                ) config
            WHERE usr.id_user_iam = tmp_id_user_iam;
        END IF;

    -- inserisce l'utente nella tabella degli utenti da replicare.
    -- vengono inserite 0, 1 o 2 righe in funzione del
    -- fatto che l'utente usi SACER, PREINGEST, entrambe o nessuna di esse.

        INSERT INTO sacer_iam.log_user_da_replic (
            id_log_user_da_replic,
            id_applic,
            id_user_iam,
            nm_userid,
            ti_oper_replic,
            ti_stato_replic,
            dt_log_user_da_replic,
            dt_chiusura_replica,
            cd_err,
            ds_msg_err,
            dt_err,
            id_log_job
        ) SELECT to_number('1000' ||TO_CHAR(slog_user_da_replic.NEXTVAL) ),
               apl.id_applic,
               usr.id_user_iam,
               usr.nm_userid,
               'MOD' ti_oper_replic,
               'DA_REPLICARE' ti_stato_replic,
               SYSDATE dt_log_user_da_replic,
               NULL dt_chiusura_replica,
               NULL cd_err,
               NULL ds_msg_err,
               NULL dt_err,
               NULL id_log_job
        FROM usr_user usr
            JOIN
                 usr_uso_user_applic uso_apl
            ON ( uso_apl.id_user_iam = usr.id_user_iam
            )
            JOIN
                 apl_applic apl
            ON ( apl.id_applic = uso_apl.id_applic
                AND apl.nm_applic IN (
                        'SACER', 'SACER_PREINGEST'
                    )
            )
        WHERE usr.id_user_iam = tmp_id_user_iam;

        -- EFFETTUA il reset della password generando una password randomica di 8 caratteri in base 64
        --
        -- MEV#24245 - rimozione della disattivazione automatica dell'utente al verificarsi di eventi 
        -- di sicurezza sull'account username/password sacer
        --
        -- Copia prima i dati della vecchia password e del salt e poi modifica la password dell'utente
        
        INSERT INTO USR_OLD_PSW (ID_OLD_PSW, ID_USER_IAM, 
                                PG_OLD_PSW, 
                                CD_PSW, CD_SALT)
        VALUES ( to_number('1000' ||TO_CHAR(SUSR_OLD_PSW.NEXTVAL)), tmp_id_user_iam, 
                (SELECT COALESCE(MAX(oldPsw.pg_Old_Psw),0) + 1 FROM Usr_Old_Psw oldPsw WHERE oldPsw.id_User_Iam = tmp_id_user_iam),
                tmp_vecchia_psw, tmp_vecchio_salt );
        
        UPDATE usr_user
            SET
                cd_psw = (select utl_encode.base64_encode(utl_raw.cast_to_raw(dbms_random.string('A', 8))) from dual),
                dt_reg_psw = SYSDATE,
                dt_scad_psw = (SYSDATE  - 1)
        WHERE id_user_iam = tmp_id_user_iam;
        
    END IF;

EXCEPTION
    WHEN no_data_found THEN
    /* se non ha trovato l'utente, esce senza fare nulla */
        NULL;
END;