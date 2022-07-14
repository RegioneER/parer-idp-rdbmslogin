create or replace PROCEDURE disattiva_utente (
/*
 * disattiva l'utente definito da nmuserid, 
 * aggiorna il log degli eventi,
 * aggiorna la tabella storica degli stati degli utenti,
 * predispone la replicazione dell'utente sugli schema collegati
 
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
 
 ex: begin DISATTIVA_UTENTE('user.name',systimestamp); end;
 */
    p_nm_userid   IN VARCHAR, -- l'utente da disattivare
    p_dt_evento   IN TIMESTAMP -- il timestamp della disattivazione
) IS

    tmp_usr_user        usr_user%rowtype;
    tmp_id_stato_user   usr_stato_user.id_stato_user%TYPE;
    tmp_id_user_iam     usr_user.id_user_iam%TYPE;
    tmp_adesso          TIMESTAMP;
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
    IF (tmp_usr_user.fl_attivo = 1
        AND (
           tmp_usr_user.dt_scad_psw IS NULL
           OR tmp_usr_user.dt_scad_psw >= tmp_adesso)
       )
    THEN
        tmp_id_user_iam     := tmp_usr_user.id_user_iam;
        SELECT susr_stato_user.NEXTVAL
        INTO
            tmp_id_stato_user
        FROM dual;

        tmp_id_stato_user   := to_number(1000 || TO_CHAR(tmp_id_stato_user) );
    
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
               'Disattivazione utente per i troppi tentativi di login falliti'
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
                    AND azio_sw.nm_azione_comp_sw = 'Disattiva per login falliti'
                    AND ti_ogg.nm_tipo_oggetto = 'Utente'
            ) config
        WHERE usr.id_user_iam = tmp_id_user_iam;

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

    -- inserisce una riga nello storico degli stati

        INSERT INTO usr_stato_user (
            id_stato_user,
            id_user_iam,
            ts_stato,
            ti_stato_user,
            id_rich_gest_user,
            id_notifica
        ) VALUES (
            tmp_id_stato_user,
            tmp_id_user_iam,
            p_dt_evento,
            'DISATTIVO',
            NULL,
            NULL
        );

    -- aggiorna lo stato dell'utente con il riferimento alla riga di stato
    -- inserita in precedenza

        UPDATE usr_user
            SET
                fl_attivo = 0,
                id_stato_user_cor = tmp_id_stato_user
        WHERE id_user_iam = tmp_id_user_iam;

    END IF;

EXCEPTION
    WHEN no_data_found THEN
    /* se non ha trovato l'utente, esce senza fare nulla */
        NULL;
END;