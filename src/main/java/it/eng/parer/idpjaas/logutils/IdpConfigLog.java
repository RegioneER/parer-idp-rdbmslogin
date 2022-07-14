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
