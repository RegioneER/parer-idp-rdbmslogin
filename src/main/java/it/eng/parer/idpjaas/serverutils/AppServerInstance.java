/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.eng.parer.idpjaas.serverutils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Francesco Fioravanti (Fioravanti_F)
 */
public class AppServerInstance {

    enum AddressTypes {
        // l'ordine di scelta dovrebbe essere quello che segue:
        // priorità ai nomi host, poi gli ipv4, poi gli ipv6.
        // nell'elenco sono presenti anche i due tipi di indirizzi di loopback
        // così che nel caso pessimo sia reso preferibilmente
        // l'indirizzo IPV4. (nel caso di questi due ultimi tipi di
        // indirizzo il nome host non è critico)
        // tendenzialmente è meglio un site local di un ip pubblico.
        // per capire se un ip ha un nome, verifico se questo è diverso dalla
        // rappresentazione in stringa delll'ip. (non molto bello, in effetti)
        SITE_LOCAL_WITH_NAME,
        NON_SITE_LOCAL_WITH_NAME,
        SITE_LOCAL_WITHOUT_NAME_IPV4,
        NON_SITE_LOCAL_WITHOUT_NAME_IPV4,
        SITE_LOCAL_WITHOUT_NAME_IPV6,
        NON_SITE_LOCAL_WITHOUT_NAME_IPV6,
        LOOPBACK_IPV4,
        LOOPBACK_IPV6
    }

    public String getName(String sname) throws UnknownHostException {
        String servername = null;
        InetAddress address = this.getMyHostAddress();
        servername = address.getCanonicalHostName();
        if (sname != null && sname.length() > 0) {
            String instance = System.getProperty(sname);
            if (instance != null) {
                servername += "/" + instance;
            }
        }
        return servername;
    }

    public InetAddress getMyHostAddress() throws UnknownHostException {
        try {
            Map<AddressTypes, InetAddress> map = new HashMap<>();

            // Nota se falliscono tutti questi tentativi la macchina è
            // probabilmente disconnessa dalla rete 
            //
            // Scorri su tutte le interfacce di rete
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // Scorri su tutti gli indirizzi IP associati ad una scheda di rete
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements();) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    AddressTypes tipo = decodeAddrType(inetAddr);
                    // se è già presente un indirizzo di questo tipo (per esempio se
                    // ci sono più schede di rete fisiche) lo sovrascrivo. Di fatto prendo
                    // l'ultimo che leggo.
                    map.put(tipo, inetAddr);
                }
            }

            // l'enum viene letto nell'ordine in cui è stato dichiarato,
            // garantendo la preferenza nella scelta del tipo di indirizzo reso
            for (AddressTypes at : AddressTypes.values()) {
                if (map.get(at) != null) {
                    return map.get(at);
                }
            }

            // A questo punto, non siamo riusciti a determinare un indirizzo plausibile
            // Ripieghiamo usando l'API del JDK sperando che il risultato non sia
            // del tutto inutile (sotto Linux la cosa è frequente)
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                throw new UnknownHostException("Il metodo JDK InetAddress.getLocalHost() ha reso null.");
            }
            return jdkSuppliedAddress;
        } catch (Exception e) {
            UnknownHostException unknownHostException
                    = new UnknownHostException("Impossibile determinare un indirizzo per la macchina: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }

    private AddressTypes decodeAddrType(InetAddress inetAddress) {
        AddressTypes tipo;
        if (!inetAddress.isLoopbackAddress()) {
            if (inetAddress.isSiteLocalAddress()) {
                if (isAddressWithHostName(inetAddress)) {
                    tipo = AddressTypes.SITE_LOCAL_WITH_NAME;
                } else if (inetAddress instanceof Inet4Address) {
                    tipo = AddressTypes.SITE_LOCAL_WITHOUT_NAME_IPV4;
                } else {
                    tipo = AddressTypes.SITE_LOCAL_WITHOUT_NAME_IPV6;
                }
            } else {
                if (isAddressWithHostName(inetAddress)) {
                    tipo = AddressTypes.NON_SITE_LOCAL_WITH_NAME;
                } else if (inetAddress instanceof Inet4Address) {
                    tipo = AddressTypes.NON_SITE_LOCAL_WITHOUT_NAME_IPV4;
                } else {
                    tipo = AddressTypes.NON_SITE_LOCAL_WITHOUT_NAME_IPV6;
                }
            }
        } else {
            // non mi interessa se questo indirizzo di loopback ha un nome:
            // nella maggior parte dei casi si chiama "localhost".
            // questa discriminazione viene fatta solo per dare
            // priorità nella selezione all'indirizzio ipv4
            // rispetto a quello ipv6
            if (inetAddress instanceof Inet4Address) {
                tipo = AddressTypes.LOOPBACK_IPV4;
            } else {
                tipo = AddressTypes.LOOPBACK_IPV6;
            }
        }
        return tipo;
    }

    private boolean isAddressWithHostName(InetAddress inetAddress) {
        if (inetAddress.getHostName().equals(inetAddress.getHostAddress())) {
            return false;
        }
        return true;
    }

}
