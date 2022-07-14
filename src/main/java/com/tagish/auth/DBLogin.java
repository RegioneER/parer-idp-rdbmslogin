package com.tagish.auth;

import it.eng.parer.idpjaas.logutils.IdpConfigLog;
import it.eng.parer.idpjaas.logutils.IdpLogger;
import it.eng.parer.idpjaas.logutils.LogDto;
import it.eng.parer.idpjaas.logutils.LogDto.TipiEvento;
import it.eng.parer.idpjaas.queryutils.NamedStatement;
import it.eng.parer.idpjaas.serverutils.AppServerInstance;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AccountExpiredException;
import javax.security.auth.login.AccountLockedException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.sql.DataSource;
import org.apache.commons.codec.binary.Base64;

/**
 * @author Andy Armstrong, <A HREF="mailto:andy@tagish.com">andy@tagish.com</A> Modificata poi da
 * @author Quaranta_M (ha aggiunto la modalità di cifratura della password del Parer)
 * @author Snidero_L (ha aggiunto la possibilità di passare il nome jndi)
 * @author Fioravanti_F (ha aggiunto il log su database dei tentativi di login falliti e la disattivazione dell'utente
 *         se questi login falliti diventano troppi)
 */
public class DBLogin extends SimpleLogin {

    private static final Logger LOGGER = Logger.getLogger(DBLogin.class.getName());

    protected String dbDriver;
    protected String dbURL;
    protected String dbUser;
    protected String dbPassword;

    protected String userTable;
    protected String userColumn;
    protected String passColumn;
    protected String saltColumn;
    protected String where;
    protected String activeColumn;
    protected String expirationColumn;
    // lookup jndi
    protected String useJndiLookup;
    protected String jndiName;
    //
    // registrazione accessi
    protected String qryRegistraEventoUtente;
    protected String serverNameSystemProperty;

    // verifica ed eventuale disattivazione utente
    // se fallisce troppi accessi
    // query: recupera parametro max giorni
    protected String qryRetrieveMaxDays;

    // query: recupera parametro max tentativi
    protected String qryRetrieveMaxTry;

    // query: determina se disattivare l'utente
    protected String qryVerificaDisattivazioneUtente;

    // query: disabilita l'utente
    protected String qryDisableUser;

    // robustezza della chiave di cifratura
    private static final int ITERATIONS = 2048;
    private static final int KEY_LENGTH = 512;

    //
    private static final String FLG_USR_ATTIVO_OFF = "0";

    @Override
    protected synchronized Vector validateUser(String username, char[] password) throws LoginException {
        String clientIpAddress = null; // verrà fornito come parametro del metodo
        String serviceProviderName = "Parer-IDP";
        //
        ResultSet rsu = null;
        Connection con = null;
        PreparedStatement psu = null;

        try {
            // lookup jndi
            if (this.useJndiLookup != null && this.useJndiLookup.equalsIgnoreCase("true")) {
                Context initContext = new InitialContext();
                DataSource ds = (DataSource) initContext.lookup(this.jndiName);
                con = ds.getConnection();
            } else {
                Class.forName(this.dbDriver);
                con = this.dbUser != null ? DriverManager.getConnection(this.dbURL, this.dbUser, this.dbPassword)
                        : DriverManager.getConnection(this.dbURL);
            }

            final String selectQuery = "SELECT " + this.passColumn + ", " + this.saltColumn + ", " + this.activeColumn
                    + ", " + this.expirationColumn + " FROM " + this.userTable + " WHERE " + this.userColumn + "=?"
                    + this.where;
            LOGGER.log(Level.FINE, "[Parer-IDP] Login query for username \"{0}\": {1}",
                    new Object[] { username, selectQuery });

            psu = con.prepareStatement(selectQuery);
            psu.setString(1, username);
            rsu = psu.executeQuery();

            // verifica se l'utente esiste.
            // se non esiste restituisce un errore di password errata
            // ma registra un errore di utente inesistente.
            //
            // L'errore di password errata viene registrato solo
            // quando è realmente tale, perché viene usato
            // per la logica di disatttivazione utente
            if (!rsu.next()) {
                LogDto tmpLogDto = new LogDto(serviceProviderName, username, TipiEvento.BAD_USER, clientIpAddress,
                        "Unknown user", new java.util.Date(), null);
                this.scriviLog(tmpLogDto, con);
                throw new FailedLoginException("Unknown user");
            }

            String storedPwd = rsu.getString(1);
            String tpwd = new String(password);
            String salt = rsu.getString(2);
            String isActive = rsu.getString(3);
            Date expDate = rsu.getDate(4);
            Date today = new Date(new java.util.Date().getTime());

            boolean isLoginOK = true;
            boolean isLoginFailed = false;
            boolean isAccountLocked = false;
            boolean isAccountExpired = false;

            // una volta verificato che l'utente esiste,
            // controllo gli eventuali errori di login.
            // Le verifiche avvengono tutte perché potrei avere più
            // errori simultaneamente e devo decidere in che
            // ordine di priorità gestirli
            if (today.after(expDate)) {
                isLoginOK = false;
                isAccountExpired = true; // pwd scaduta
            }
            if (isActive.equals(FLG_USR_ATTIVO_OFF)) {
                isLoginOK = false;
                isAccountLocked = true; // utente disattivato
            }
            if (salt == null || salt.equals("") ? !storedPwd.equals(this.encodePassword(tpwd))
                    : !this.validatePassword(salt, tpwd, storedPwd)) {
                isLoginOK = false;
                isLoginFailed = true; // password errata
            }

            // preparo la risposta
            Vector<TypedPrincipal> typedPrincipalVector = null;
            LogDto tmpLogDto = null;
            if (isLoginOK) {
                // se tutto è andato bene, preparo la risposta
                Vector<TypedPrincipal> p = new Vector<TypedPrincipal>();
                p.add(new TypedPrincipal(username, 1));
                typedPrincipalVector = p;
                tmpLogDto = new LogDto(serviceProviderName, username, TipiEvento.LOGIN_OK, clientIpAddress, "Login OK",
                        new java.util.Date(), null);
            } else {
                // altrimenti verifico in ordine di priorità che errore registrare
                if (isLoginFailed) {
                    // se la password è errata, lo registro in tabella.
                    tmpLogDto = new LogDto(serviceProviderName, username, TipiEvento.BAD_PASS, clientIpAddress,
                            "Bad password", new java.util.Date(), null);
                } else if (isAccountLocked) {
                    // se l'utente è disattivato, lo registro in tabella
                    tmpLogDto = new LogDto(serviceProviderName, username, TipiEvento.LOCKED, clientIpAddress,
                            "Account disabled", new java.util.Date(), null);
                } else if (isAccountExpired) {
                    // se l'utente è scaduto, lo registro in tabella
                    tmpLogDto = new LogDto(serviceProviderName, username, TipiEvento.EXPIRED, clientIpAddress,
                            "Account expired on " + expDate.toString(), new java.util.Date(), null);
                }
            }
            // scrittura dell'evento ed eventuale disattivazione utente
            this.scriviLog(tmpLogDto, con);

            if (!isLoginOK) {
                LOGGER.log(Level.FINE, "[Parer-IDP] Failed login; User: {0}, Cause {1}",
                        new Object[] { tmpLogDto.getNmUser(), tmpLogDto.getTipoEvento().name() });
            }

            // verifico in ordine di priorità che messaggio di errore
            // rendere all'utente (la priorita potrebbe essere diversa dal caso precedente)
            if (isLoginFailed) {
                // se la password è errata, rendo questo errore, indipendentemente da altri errori
                throw new FailedLoginException("Bad password");
            } else if (isAccountLocked) {
                // altrimenti se l'utente è disattivato, rendo questo errore, indipendentemente da altri errori
                throw new AccountLockedException("Account disabled");
            } else if (isAccountExpired) {
                // altrimenti se la password è scaduta, rendo questo errore, in questo modo l'utente possa cambiarla
                throw new AccountExpiredException("Account expired on " + expDate);
            }

            // nota bene: questo Vector viene reso solo se l'autenticazione
            // è andata bene, in tutti gli altri casi questo metodo esce con un'eccezione
            return typedPrincipalVector;
        } catch (ClassNotFoundException e) {
            throw new LoginException("Error reading user database (" + e.getMessage() + ")");
        } catch (SQLException e) {
            throw new LoginException("Error reading user database (" + e.getMessage() + ")");
        } catch (UnknownHostException e) {
            throw new LoginException("Error reading server name (" + e.getMessage() + ")");
        } catch (NoSuchAlgorithmException e) {
            throw new LoginException("Error SHA-1 algo not supported");
        } catch (UnsupportedEncodingException e) {
            throw new LoginException("Error UTF-8 not supported");
        } catch (InvalidKeySpecException e) {
            throw new LoginException(e.getMessage());
        } catch (NamingException e) {
            throw new LoginException(e.getMessage());
        } finally {
            try {
                if (rsu != null) {
                    rsu.close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[Parer-IDP] eccezione durante " + "la chiusura del ResultSet \"rsu\"", e);
            }
            try {
                if (psu != null) {
                    psu.close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "[Parer-IDP] eccezione durante " + "la chiusura del PreparedStatement \"psu\"", e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[Parer-IDP] eccezione durante " + "la chiusura della Connection \"con\"", e);
            }
        }
    }

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map sharedState, Map options) {
        super.initialize(subject, callbackHandler, sharedState, options);

        this.useJndiLookup = this.getOption("useJndiLookup", null);
        this.jndiName = this.getOption("jndiName", null);

        // Se utilizzo jndi non utilizzo la connessione diretta
        if (this.useJndiLookup == null || !this.useJndiLookup.equalsIgnoreCase(("true"))) {

            this.dbDriver = this.getOption("dbDriver", null);
            if (this.dbDriver == null) {
                throw new Error("No database driver named (dbDriver=?)");
            }
            this.dbURL = this.getOption("dbURL", null);
            if (this.dbURL == null) {
                throw new Error("No database URL specified (dbURL=?)");
            }
            this.dbUser = this.getOption("dbUser", null);
            this.dbPassword = this.getOption("dbPassword", null);
            if (this.dbUser == null && this.dbPassword != null || this.dbUser != null && this.dbPassword == null) {
                throw new Error("Either provide dbUser and dbPassword or encode both in dbURL");
            }
        }

        this.userTable = this.getOption("userTable", "User");
        this.userColumn = this.getOption("userColumn", "user_name");
        this.passColumn = this.getOption("passColumn", "user_passwd");
        this.saltColumn = this.getOption("saltColumn", "");
        this.where = this.getOption("where", "");
        this.activeColumn = this.getOption("activeColumn", "");
        this.expirationColumn = this.getOption("expirationColumn", "");
        this.where = this.where != null && this.where.length() > 0 ? " AND " + this.where : "";

        this.qryRegistraEventoUtente = this.getOption("qryRegistraEventoUtente", null);

        // Se non è definita la query di log, ignoro i rimanenti settaggi
        // e non registro i tentativi di login falliti, ovviamente
        if (this.qryRegistraEventoUtente != null && !this.qryRegistraEventoUtente.isEmpty()) {

            this.serverNameSystemProperty = this.getOption("serverNameSystemProperty", null);
            // questo valore può essere nullo, in questo caso il nome del server
            // riporterà solo il nome della macchina fisica e non dell'istanza

            this.qryRetrieveMaxDays = this.getOption("qryRetrieveMaxDays", null);
            this.qryRetrieveMaxTry = this.getOption("qryRetrieveMaxTry", null);
            this.qryVerificaDisattivazioneUtente = this.getOption("qryVerificaDisattivazioneUtente", null);
            this.qryDisableUser = this.getOption("qryDisableUser", null);
        }
        // Dump dei parametri configurati
        StringBuilder attributes = new StringBuilder("{").append(System.lineSeparator());
        try {
            for (Field declaredField : this.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(declaredField.getModifiers())) {
                    attributes.append(declaredField.getName());
                    attributes.append(" = ");
                    attributes.append((String) declaredField.get(this));
                    attributes.append(System.lineSeparator());
                }

            }
        } catch (IllegalAccessException e) {
            attributes.append("eccezione durante la lettura dei parametri : ").append(e.getMessage());
            LOGGER.log(Level.WARNING, "[Parer-IDP] eccezione durante " + "la lettura dei parametri di configurazione",
                    e);
        }
        attributes.append("}").append(System.lineSeparator());
        LOGGER.log(Level.FINE, "[Parer-IDP] Configurazione SecuritySubsystem {0}",
                new Object[] { attributes.toString() });
    }

    /**
     * Genera l'hash senza SALT
     *
     * @param password
     * 
     * @return hash della password
     * 
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    private String encodePassword(String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(password.getBytes("UTF-8"), 0, password.length());
        byte[] pwdHash = md.digest();
        return new String(Base64.encodeBase64((byte[]) pwdHash), "UTF-8");
    }

    /**
     * Genera l'hash della password utilizzando il salt (preso dal DB)
     *
     * @param salt
     * @param password
     * @param storedPassword
     * 
     * @return hash della password
     * 
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws UnsupportedEncodingException
     */
    private boolean validatePassword(String salt, String password, String storedPassword)
            throws NoSuchAlgorithmException, InvalidKeySpecException, UnsupportedEncodingException {
        byte[] binarySalt = Base64.decodeBase64((byte[]) salt.getBytes("UTF-8"));
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), binarySalt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] testHash = skf.generateSecret(spec).getEncoded();
        return storedPassword.equals(new String(Base64.encodeBase64((byte[]) testHash), "UTF-8"));
    }

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
     * @throws UnknownHostException
     * @throws SQLException
     * 
     * @see AppServerInstance
     * @see NamedStatement
     */
    private void scriviLog(LogDto logDto, Connection con) throws UnknownHostException, SQLException {

        if (this.qryRegistraEventoUtente != null && !this.qryRegistraEventoUtente.isEmpty()) {
            NamedStatement ns = null;
            ResultSet rs = null;
            String query;
            int giorni = 0;
            int tentativi = 0;
            /*
             * Nota, questo è un trucco per rendere il sistema più veloce: IdpConfigLog deve sempre contenere i
             * parametri relativi a maxGiorni e maxtentativi, ma questi vengono usati solo quando il tipo evento e
             * BAD_PASS. Con questa condizione, le query di lettura dei parametri vengono eseguite solo in questo caso,
             * mentre negli altri casi vengono usati i valori 0,0 che verranno comunque ignorati dal metodo scriviLog().
             */
            if (logDto.getTipoEvento() == TipiEvento.BAD_PASS) {
                try {
                    String maxtentativi = "";
                    String maxGiorni = "";
                    //
                    query = this.qryRetrieveMaxTry;
                    ns = new NamedStatement(con, query);
                    LOGGER.log(Level.FINE, "[Parer-IDP] Failed login; get max try: {0} for object {1}",
                            new Object[] { query, logDto.toString() });
                    rs = ns.executeQuery();
                    if (rs.next()) {
                        maxtentativi = rs.getString(1);
                    }
                    query = this.qryRetrieveMaxDays;
                    ns = new NamedStatement(con, query);
                    LOGGER.log(Level.FINE, "[Parer-IDP] Failed login; get max days: {0} for object {1}",
                            new Object[] { query, logDto.toString() });
                    rs = ns.executeQuery();
                    if (rs.next()) {
                        maxGiorni = rs.getString(1);
                    }
                    if ((!maxtentativi.isEmpty()) && (!maxGiorni.isEmpty())) {
                        giorni = Integer.parseInt(maxGiorni);
                        tentativi = Integer.parseInt(maxtentativi);
                    }
                } finally {
                    try {
                        if (rs != null) {
                            rs.close();
                        }
                    } catch (SQLException e) {
                        // ho appena fatto sparire un'eccezione e lo so.
                        LOGGER.log(Level.WARNING, "[Parer-IDP] eccezione durante " + "la chiusura del ResultSet \"rs\"",
                                e);
                    }
                    try {
                        if (ns != null) {
                            ns.close();
                        }
                    } catch (SQLException e) {
                        // ho appena fatto sparire un'eccezione e lo so.
                        LOGGER.log(Level.WARNING,
                                "[Parer-IDP] eccezione durante " + "la chiusura del NamedStatement \"ns\"", e);
                    }
                }
            }

            IdpConfigLog tmpConfigLog = new IdpConfigLog();
            tmpConfigLog.setMaxGiorni(giorni);
            tmpConfigLog.setMaxTentativi(tentativi);

            tmpConfigLog.setQryVerificaDisattivazioneUtente(this.qryVerificaDisattivazioneUtente);
            tmpConfigLog.setQryDisabilitaUtente(this.qryDisableUser);
            tmpConfigLog.setQryRegistraEventoUtente(this.qryRegistraEventoUtente);

            tmpConfigLog.setServerNameSystemProperty(this.serverNameSystemProperty);

            try {
                con.setAutoCommit(false);
                // tutte le scritture su database avvengono in un'unica transazione
                IdpLogger.EsitiLog risposta = (new IdpLogger(tmpConfigLog).scriviLog(logDto, con));
                con.commit();
                con.setAutoCommit(true);
                if (risposta == IdpLogger.EsitiLog.UTENTE_DISATTIVATO) {
                    LOGGER.log(Level.FINE, "[Parer-IDP] Failed login; disable user: {0} ", logDto.getNmUser());
                }
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

}
