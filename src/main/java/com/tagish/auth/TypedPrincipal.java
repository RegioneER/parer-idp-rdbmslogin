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

// $Id: TypedPrincipal.java,v 1.7 2003/02/17 20:13:23 andy Exp $
package com.tagish.auth;

import java.io.*;
import java.security.Principal;

/**
 * TypedPrincipals are derived from, and can be treated like Principals but they also contain extra information about
 * the type of the Principal which can be USER, GROUP or DOMAIN. I'm not 100% certain that this is a good way of doing
 * things. Suggestions welcome.
 *
 * @author Andy Armstrong
 * 
 * @version 1.0.3
 */
public class TypedPrincipal implements Principal, Serializable {
    /**
     */
    protected String name;
    protected int type;
    static final protected long serialVersionUID = 1234567894326643L;

    /**
     * This <CODE>TypedPrincipal</CODE> represents a username or SID.
     */
    public final static int USER = 1;

    /**
     * This <code>TypedPrincipal</code> represents the domain name or SID.
     */
    public final static int DOMAIN = 2;

    /**
     * This <code>TypedPrincipal</code> represents a group name or SID.
     */
    public final static int GROUP = 3;

    /**
     * This <code>TypedPrincipal</code> represents an item of unknown type.
     */
    public final static int UNKNOWN = 4;

    protected final static String typeMap[] = { null, "USER", "DOMAIN", "GROUP", "UNKNOWN" };

    /**
     * Create a TypedPrincipal with a name and type.
     *
     * @param name
     *            the name for this principal.
     * @param type
     *            the type for this principal.
     * 
     * @exception NullPointerException
     *                if the <code>name</code> is <code>null</code>.
     */
    public TypedPrincipal(String name, int type) {
        if (name == null) {
            throw new NullPointerException("Illegal null name");
        }
        if (type < USER || type > UNKNOWN) {
            throw new IllegalArgumentException("Bad type value");
        }

        this.name = name;
        this.type = type;
    }

    /**
     * Create a TypedPrincipal with a name.
     *
     * @param name
     *            the name for this Principal.
     * 
     * @exception NullPointerException
     *                if the <code>name</code> is <code>null</code>.
     */
    public TypedPrincipal(String name) {
        this(name, UNKNOWN);
    }

    /**
     * Create a TypedPrincipal with a blank name.
     *
     */
    public TypedPrincipal() {
        this("", UNKNOWN);
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return the name for this <code>TypedPrincipal</code>.
     *
     * @return the name for this <code>TypedPrincipal</code>
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Return the type for this <code>TypedPrincipal</code>.
     *
     * @return the type for this <code>TypedPrincipal</code>
     */
    public int getType() {
        return type;
    }

    /**
     * Return the name of the type for this <code>TypedPrincipal</code>.
     *
     * @return the name of the type for this <code>TypedPrincipal</code>
     */
    public String getTypeName() {
        return typeMap[type];
    }

    /**
     * Return a string representation of this <code>TypedPrincipal</code>.
     *
     * @return a string representation of this <code>TypedPrincipal</code>.
     */
    @Override
    public String toString() {
        String className = getClass().getName();
        return className.substring(className.lastIndexOf('.') + 1) + ": " + name + " [" + getTypeName() + "]";
    }

    /**
     * Compares the specified Object with this <code>TypedPrincipal</code> for equality. Returns true if the given
     * object is also a <code>TypedPrincipal</code> and the two TypedPrincipals have the same name and type. If the
     * object is any other kind Principal its will be considered equal if the name matches.
     *
     * @param o
     *            Object to be compared for equality with this <code>TypedPrincipal</code>.
     * 
     * @return true if the specified Object is equal to this <code>TypedPrincipal</code>.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (this == o) {
            return true;
        } else if (o instanceof TypedPrincipal) {
            TypedPrincipal that = (TypedPrincipal) o;
            return that.getName().equals(getName()) && that.getType() == getType();
        } else if (o instanceof Principal) {
            Principal that = (Principal) o;
            return that.getName().equals(getName());
        } else {
            return false;
        }
    }

    /**
     * Return a hash code for this <code>TypedPrincipal</code>.
     *
     * @return a hash code for this <code>TypedPrincipal</code>.
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
