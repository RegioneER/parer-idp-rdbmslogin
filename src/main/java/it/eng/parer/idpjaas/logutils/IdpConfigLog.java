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

/**
 *
 * @author fioravanti_f
 */
public class IdpConfigLog {

    private String qryVerificaDisattivazioneUtente;
    private String qryRegistraEventoUtente;
    private String qryDisabilitaUtente;

    private int maxTentativi;
    private int maxGiorni;

    private String serverNameSystemProperty;

    @Override
    public String toString() {
        return "IdpConfigLog{" + "qryVerificaDisattivazioneUtente=" + qryVerificaDisattivazioneUtente
                + ", qryRegistraEventoUtente=" + qryRegistraEventoUtente + ", qryDisabilitaUtente="
                + qryDisabilitaUtente + ", maxTentativi=" + maxTentativi + ", maxGiorni=" + maxGiorni
                + ", serverNameSystemProperty=" + serverNameSystemProperty + '}';
    }

    public String getQryVerificaDisattivazioneUtente() {
        return qryVerificaDisattivazioneUtente;
    }

    public void setQryVerificaDisattivazioneUtente(String qryVerificaDisattivazioneUtente) {
        this.qryVerificaDisattivazioneUtente = qryVerificaDisattivazioneUtente;
    }

    public String getQryRegistraEventoUtente() {
        return qryRegistraEventoUtente;
    }

    public void setQryRegistraEventoUtente(String qryRegistraEventoUtente) {
        this.qryRegistraEventoUtente = qryRegistraEventoUtente;
    }

    public String getQryDisabilitaUtente() {
        return qryDisabilitaUtente;
    }

    public void setQryDisabilitaUtente(String qryDisabilitaUtente) {
        this.qryDisabilitaUtente = qryDisabilitaUtente;
    }

    public int getMaxTentativi() {
        return maxTentativi;
    }

    public void setMaxTentativi(int maxTentativi) {
        this.maxTentativi = maxTentativi;
    }

    public int getMaxGiorni() {
        return maxGiorni;
    }

    public void setMaxGiorni(int maxGiorni) {
        this.maxGiorni = maxGiorni;
    }

    public String getServerNameSystemProperty() {
        return serverNameSystemProperty;
    }

    public void setServerNameSystemProperty(String serverNameSystemProperty) {
        this.serverNameSystemProperty = serverNameSystemProperty;
    }

}
