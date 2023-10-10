/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.eng.parer.idpjaas.logutils;

import java.util.Date;

/**
 *
 * @author fioravanti_f
 */
public class LogDto {

    public enum TipiEvento {

        BAD_PASS, BAD_USER, EXPIRED, LOCKED, LOGIN_OK, SET_PSW
    }

    private String nmAttore;
    private String nmUser;
    private TipiEvento tipoEvento;
    String cdIndIpClient;
    String dsEvento;
    private java.util.Date tsEvento;
    private String servername;

    @Override
    public String toString() {
        return "LogDto{"
                + "nmAttore=" + nmAttore
                + ", nmUser=" + nmUser
                + ", tipoEvento=" + tipoEvento
                + ", cdIndIpClient=" + cdIndIpClient
                + ", dsEvento=" + dsEvento
                + ", tsEvento=" + tsEvento
                + ", servername=" + servername
                + '}';
    }

    public LogDto() {
    }

    public LogDto(String nmAttore, String nmUser,
            TipiEvento tipoEvento, String cdIndIpClient,
            String dsEvento, Date tsEvento,
            String servername) {
        this.nmAttore = nmAttore;
        this.nmUser = nmUser;
        this.tipoEvento = tipoEvento;
        this.cdIndIpClient = cdIndIpClient;
        this.dsEvento = dsEvento;
        this.tsEvento = tsEvento;
        this.servername = servername;
    }

    public String getNmAttore() {
        return nmAttore;
    }

    public void setNmAttore(String nmAttore) {
        this.nmAttore = nmAttore;
    }

    public String getNmUser() {
        return nmUser;
    }

    public void setNmUser(String nmUser) {
        this.nmUser = nmUser;
    }

    public TipiEvento getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(TipiEvento tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getCdIndIpClient() {
        return cdIndIpClient;
    }

    public void setCdIndIpClient(String cdIndIpClient) {
        this.cdIndIpClient = cdIndIpClient;
    }

    public String getDsEvento() {
        return dsEvento;
    }

    public void setDsEvento(String dsEvento) {
        this.dsEvento = dsEvento;
    }

    public Date getTsEvento() {
        return tsEvento;
    }

    public void setTsEvento(Date tsEvento) {
        this.tsEvento = tsEvento;
    }

    public String getServername() {
        return servername;
    }

    public void setServername(String servername) {
        this.servername = servername;
    }

}
