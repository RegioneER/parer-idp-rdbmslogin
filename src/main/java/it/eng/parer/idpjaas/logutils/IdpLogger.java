/*
 * Engineering Ingegneria Informatica S.p.A.
 *
 * Copyright (C) 2023 Regione Emilia-Romagna
 * <p/>
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.eng.parer.idpjaas.logutils;

import it.eng.parer.idpjaas.queryutils.NamedStatement;
import it.eng.parer.idpjaas.serverutils.AppServerInstance;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 *
 * @author fioravanti_f
 */
public class IdpLogger {

    /**
     * UTENTE_OK, UTENTE_DISATTIVATO
     */
    public enum EsitiLog {
        UTENTE_OK, UTENTE_DISATTIVATO
    }

    private final IdpConfigLog idpConfigLog;

    public IdpLogger(IdpConfigLog configurazione) {
        this.idpConfigLog = configurazione;
    }

    /*
     * nmUser tipoEvento tsEvento dsEvento cdIndIpClient cdIndServer maxTentativi maxGiorni
     */
    /**
     * Scrive - se necessario - una riga nella tabella di log, ed eventualmente disattiva l'utente usando la
     * {@link Connection} fornita ed i parametri indicati nel DTO. Nel caso i parametri non siano indicati nella query
     * di inserimento, non verrà prodotto un errore, ma questi verranno semplicemente ignorati.
     *
     * @param logDto
     *            istanza di {@link LogDto} contenente i parametri
     * @param con
     *            istanza di {@link Connection} aperta.
     * 
     * @return istanza di {@link EsitiLog} che indica se l'utente è stato disattivato o meno
     * 
     * @throws UnknownHostException
     * @throws SQLException
     * 
     * @see AppServerInstance
     * @see NamedStatement
     */
    public EsitiLog scriviLog(LogDto logDto, Connection con) throws UnknownHostException, SQLException {
        EsitiLog retval = EsitiLog.UTENTE_OK;
        if (idpConfigLog.getQryRegistraEventoUtente() != null && !idpConfigLog.getQryRegistraEventoUtente().isEmpty()) {
            Timestamp tsEvento = new Timestamp(logDto.getTsEvento().getTime());
            NamedStatement ns = null;
            ResultSet rs = null;
            String query;
            boolean disattiva = false;
            try {
                if (logDto.getTipoEvento() == LogDto.TipiEvento.BAD_PASS
                        && idpConfigLog.getQryVerificaDisattivazioneUtente() != null) {
                    query = idpConfigLog.getQryVerificaDisattivazioneUtente();
                    ns = new NamedStatement(con, query);
                    ns.setString(NamedStatement.Modes.QUIET, "nmUser", logDto.getNmUser());
                    ns.setString(NamedStatement.Modes.QUIET, "tipoEvento", logDto.getTipoEvento().name());
                    ns.setInt(NamedStatement.Modes.QUIET, "maxTentativi", idpConfigLog.getMaxTentativi());
                    ns.setInt(NamedStatement.Modes.QUIET, "maxGiorni", idpConfigLog.getMaxGiorni());
                    ns.setTimestamp(NamedStatement.Modes.QUIET, "tsEvento", tsEvento);
                    rs = ns.executeQuery();
                    if (rs.next()) {
                        String result = rs.getString(1);
                        if (result.equals("DISATTIVARE")) {
                            // se ho superato il massimo di tentativi consentiti, disattivo l'utente;
                            // in ogni caso chi decide è la query che invoca una stored function
                            disattiva = true;
                        }
                    }
                }

                String descEstesa = "";
                // se l'errore e BAD_PASS ed allo step precedente ho rilevato le condizioni,
                // disattivo l'utente tramite stored procedure
                if (disattiva) {
                    // MEV#24245 - rimozione della disattivazione automatica dell'utente al verificarsi di eventi di
                    // sicurezza sull'account username/password sacer
                    descEstesa = "/ all'utente viene resettata la password";
                    query = idpConfigLog.getQryDisabilitaUtente();
                    ns = new NamedStatement(con, query);
                    ns.setString(NamedStatement.Modes.QUIET, "nmUser", logDto.getNmUser());
                    ns.setTimestamp(NamedStatement.Modes.QUIET, "tsEvento", tsEvento);
                    ns.executeUpdate();
                    retval = EsitiLog.UTENTE_DISATTIVATO;
                }

                // in ogni caso registro in tabella l'evento di login
                query = idpConfigLog.getQryRegistraEventoUtente();
                ns = new NamedStatement(con, query);

                // se mi viene fornito il nome del server nel dto, lo uso come fornito
                // se mi viene fornito il nome della property del container,
                // calcolo il nome server completo (nome server/nome istanza del container)
                // se non mi viene fornito nulla, il nome server sarà calcolato
                // senza il nome dell'istanza del container
                String cdIndServer;
                if (logDto.getServername() != null && !logDto.getServername().isEmpty()) {
                    cdIndServer = logDto.getServername();
                } else if (this.idpConfigLog.getServerNameSystemProperty() != null
                        && !this.idpConfigLog.getServerNameSystemProperty().isEmpty()) {
                    cdIndServer = new AppServerInstance().getName(this.idpConfigLog.getServerNameSystemProperty());
                } else {
                    cdIndServer = new AppServerInstance().getName(null);
                }
                String descrizione = logDto.getNmAttore() + " - " + logDto.getDsEvento() + descEstesa;
                ns.setString(NamedStatement.Modes.QUIET, "nmUser", logDto.getNmUser());
                ns.setString(NamedStatement.Modes.QUIET, "cdIndIpClient", logDto.getCdIndIpClient());
                ns.setString(NamedStatement.Modes.QUIET, "cdIndServer", cdIndServer);
                ns.setString(NamedStatement.Modes.QUIET, "tipoEvento", logDto.getTipoEvento().name());
                ns.setString(NamedStatement.Modes.QUIET, "dsEvento", descrizione);
                ns.setTimestamp(NamedStatement.Modes.QUIET, "tsEvento", tsEvento);
                ns.executeUpdate();
            } finally {
                try {
                    if (rs != null) {
                        rs.close();
                    }
                } catch (SQLException e) {
                    // ho appena fatto sparire un'eccezione e lo so.
                }
                try {
                    if (ns != null) {
                        ns.close();
                    }
                } catch (SQLException e) {
                    // ho appena fatto sparire un'eccezione e lo so.
                }
            }
        }
        return retval;
    }

}
