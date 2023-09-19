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

// $Id: Utils.java,v 1.5 2003/02/17 20:13:23 andy Exp $
package com.tagish.auth;

import java.security.*;

/**
 * Utility methods for com.tagish.auth.*. All the methods in here are static so Utils should never be instantiated.
 *
 * @author Andy Armstrong, <A HREF="mailto:andy@tagish.com">andy@tagish.com</A>
 * 
 * @version 1.0.3
 */
public class Utils {
    private final static String ALGORITHM = "MD5";
    private static MessageDigest md = null;

    /**
     * Can't make these: all the methods are static
     */
    private Utils() {
    }

    /**
     * Turn a byte array into a char array containing a printable hex representation of the bytes. Each byte in the
     * source array contributes a pair of hex digits to the output array.
     *
     * @param src
     *            the source array
     * 
     * @return a char array containing a printable version of the source data
     */
    private static char[] hexDump(byte src[]) {
        char buf[] = new char[src.length * 2];
        for (int b = 0; b < src.length; b++) {
            String byt = Integer.toHexString((int) src[b] & 0xFF);
            if (byt.length() < 2) {
                buf[b * 2 + 0] = '0';
                buf[b * 2 + 1] = byt.charAt(0);
            } else {
                buf[b * 2 + 0] = byt.charAt(0);
                buf[b * 2 + 1] = byt.charAt(1);
            }
        }
        return buf;
    }

    /**
     * Zero the contents of the specified array. Typically used to erase temporary storage that has held plaintext
     * passwords so that we don't leave them lying around in memory.
     *
     * @param pwd
     *            the array to zero
     */
    public static void smudge(char pwd[]) {
        if (null != pwd) {
            for (int b = 0; b < pwd.length; b++) {
                pwd[b] = 0;
            }
        }
    }

    /**
     * Zero the contents of the specified array.
     *
     * @param pwd
     *            the array to zero
     */
    public static void smudge(byte pwd[]) {
        if (null != pwd) {
            for (int b = 0; b < pwd.length; b++) {
                pwd[b] = 0;
            }
        }
    }

    /**
     * Perform MD5 hashing on the supplied password and return a char array containing the encrypted password as a
     * printable string. The hash is computed on the low 8 bits of each character.
     *
     * @param pwd
     *            The password to encrypt
     * 
     * @return a character array containing a 32 character long hex encoded MD5 hash of the password
     * 
     * @throws java.lang.Exception
     *             cannot load {@link Utils#ALGORITHM}
     */
    public static char[] cryptPassword(char pwd[]) throws Exception {
        if (null == md) {
            md = MessageDigest.getInstance(ALGORITHM);
        }
        md.reset();
        byte pwdb[] = new byte[pwd.length];
        for (int b = 0; b < pwd.length; b++) {
            pwdb[b] = (byte) pwd[b];
        }
        char crypt[] = hexDump(md.digest(pwdb));
        smudge(pwdb);
        return crypt;
    }
}
