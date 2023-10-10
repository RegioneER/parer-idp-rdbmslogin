/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.eng.parer.idpjaas.queryutils;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class wraps around a {@link PreparedStatement} and allows the programmer to set parameters by name instead of by
 * index. This eliminates any confusion as to which parameter index represents what. This also means that rearranging
 * the SQL statement or adding a parameter doesn't involve renumbering your indices.
 *
 * Credit to Adam Crume for his implementation of the SQL query parser:
 * http://www.javaworld.com/article/2077706/core-java/named-parameters-for-preparedstatement.html
 *
 * @author Francesco Fioravanti (Fioravanti_F)
 */
public class NamedStatement {

    /**
     * The statement this object is wrapping.
     */
    private final PreparedStatement statement;

    /**
     * Maps parameter names to arrays of ints which are the parameter indices.
     */
    private final Map<String, List<Integer>> indexMap;

    public enum Modes {
        /**
         * With this option set, the setXxxx method will throw an exception if the parameter does not exist
         */
        STANDARD,
        /**
         * With this option set, the setXxxx method will NOT throw an exception if the parameter does not exist
         */
        QUIET
    }

    /**
     * Creates a NamedStatement. Wraps a call to {@link Connection#prepareStatement(java.lang.String)}.
     *
     * @param connection
     *            the database connection
     * @param query
     *            the parameterized query
     * 
     * @throws SQLException
     *             if the statement could not be created
     * 
     * @see Connection#prepareStatement(java.lang.String)
     */
    public NamedStatement(Connection connection, String query) throws SQLException {
        indexMap = new HashMap();
        String parsedQuery = parseQuery(query);
        statement = connection.prepareStatement(parsedQuery);
    }

    /**
     * Parses a query with named parameters. The parameter-index mappings are put into the map, and the parsed query is
     * returned.
     *
     * @param query
     *            query to parse
     * 
     * @return the parsed query
     */
    private String parseQuery(String query) {
        int queryTextLength = query.length();
        StringBuffer parsedQuery = new StringBuffer(queryTextLength);
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int parameterIndex = 1;

        for (int i = 0; i < queryTextLength; i++) {
            char tmpChar = query.charAt(i);
            if (inSingleQuote) {
                if (tmpChar == '\'') {
                    inSingleQuote = false;
                }
            } else if (inDoubleQuote) {
                if (tmpChar == '"') {
                    inDoubleQuote = false;
                }
            } else {
                if (tmpChar == '\'') {
                    inSingleQuote = true;
                } else if (tmpChar == '"') {
                    inDoubleQuote = true;
                } else if (tmpChar == ':' && i + 1 < queryTextLength
                        && Character.isJavaIdentifierStart(query.charAt(i + 1))) {
                    int j = i + 2;
                    while (j < queryTextLength && Character.isJavaIdentifierPart(query.charAt(j))) {
                        j++;
                    }
                    String tmpParamName = query.substring(i + 1, j);
                    tmpChar = '?'; // replace the parameter with a question mark
                    i += tmpParamName.length(); // skip past the end of the parameter

                    List<Integer> indexList = (List<Integer>) indexMap.get(tmpParamName);
                    if (indexList == null) {
                        indexList = new ArrayList<>();
                        indexMap.put(tmpParamName, indexList);
                    }
                    indexList.add(parameterIndex);
                    parameterIndex++;
                }
            }
            parsedQuery.append(tmpChar);
        }

        return parsedQuery.toString();
    }

    /**
     * Tests if a parameter exists in the parsed querystring
     *
     * @param name
     *            the parameter/placeholder to test for
     * 
     * @return true if the parameter is referenced in query string
     */
    public boolean isPlaceHolderInQueryString(String name) {
        if (indexMap.containsKey(name)) {
            return true;
        }
        return false;
    }

    /**
     * Returns the indexes for a parameter.
     *
     * @param name
     *            parameter name
     * 
     * @return parameter indexes
     * 
     * @throws IllegalArgumentException
     *             if the parameter does not exist
     */
    private List<Integer> getIndexesOrDieTrying(String name) {
        List<Integer> indexes = (List<Integer>) indexMap.get(name);
        if (indexes == null) {
            throw new IllegalArgumentException("Parameter not found: " + name);
        }
        return indexes;
    }

    /**
     * Returns the indexes for a parameter; does not throw an exception if the parameter is not found
     *
     * @param name
     *            parameter name
     * 
     * @return parameter indexes
     */
    private List<Integer> getIndexes(String name) {
        List<Integer> indexes = (List<Integer>) indexMap.get(name);
        return indexes;
    }

    /**
     * Sets a parameter.
     *
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist
     * 
     * @see PreparedStatement#setObject(int, java.lang.Object)
     */
    public void setObject(String name, Object value) throws SQLException {
        this.setObject(Modes.STANDARD, name, value);
    }

    /**
     * Sets a parameter.
     *
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist
     * 
     * @see PreparedStatement#setString(int, java.lang.String)
     */
    public void setString(String name, String value) throws SQLException {
        this.setString(Modes.STANDARD, name, value);
    }

    /**
     * Sets a parameter.
     *
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist
     * 
     * @see PreparedStatement#setInt(int, int)
     */
    public void setInt(String name, Integer value) throws SQLException {
        this.setInt(Modes.STANDARD, name, value);
    }

    /**
     * Sets a parameter.
     *
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist
     * 
     * @see PreparedStatement#setLong(int, long)
     */
    public void setLong(String name, Long value) throws SQLException {
        this.setLong(Modes.STANDARD, name, value);
    }

    /**
     * Sets a parameter.
     *
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist
     * 
     * @see PreparedStatement#setTimestamp(int, java.sql.Timestamp)
     */
    public void setTimestamp(String name, Timestamp value) throws SQLException {
        this.setTimestamp(Modes.STANDARD, name, value);
    }

    /**
     * Sets a parameter.
     *
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist
     * 
     * @see PreparedStatement#setDate(int, java.sql.Date)
     */
    public void setDate(String name, Date value) throws SQLException {
        this.setDate(Modes.STANDARD, name, value);
    }

    /**
     * Sets a parameter.
     *
     * @param mode
     *            values: STANDARD: same behaviour as normal method, QUIET: doesn't throw an exception if the parameter
     *            does not exist
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist and mode is set to STANDARD
     * 
     * @see PreparedStatement#setObject(int, java.lang.Object)
     */
    public void setObject(Modes mode, String name, Object value) throws SQLException {
        List<Integer> indexes = null;
        if (mode == Modes.STANDARD) {
            indexes = getIndexesOrDieTrying(name);
        } else {
            indexes = getIndexes(name);
            if (indexes == null) {
                return;
            }
        }
        for (Integer i : indexes) {
            if (value != null) {
                statement.setObject(i, value);
            } else {
                statement.setNull(i, java.sql.Types.OTHER);
            }
        }
    }

    /**
     * Sets a parameter.
     *
     * @param mode
     *            values: STANDARD: same behaviour as normal method, QUIET: doesn't throw an exception if the parameter
     *            does not exist
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist and mode is set to STANDARD
     * 
     * @see PreparedStatement#setString(int, java.lang.String)
     */
    public void setString(Modes mode, String name, String value) throws SQLException {
        List<Integer> indexes = null;
        if (mode == Modes.STANDARD) {
            indexes = getIndexesOrDieTrying(name);
        } else {
            indexes = getIndexes(name);
            if (indexes == null) {
                return;
            }
        }
        for (Integer i : indexes) {
            if (value != null) {
                statement.setString(i, value);
            } else {
                statement.setNull(i, java.sql.Types.VARCHAR);
            }
        }
    }

    /**
     * Sets a parameter.
     *
     * @param mode
     *            values: STANDARD: same behaviour as normal method, QUIET: doesn't throw an exception if the parameter
     *            does not exist
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist and mode is set to STANDARD
     * 
     * @see PreparedStatement#setInt(int, int)
     */
    public void setInt(Modes mode, String name, Integer value) throws SQLException {
        List<Integer> indexes = null;
        if (mode == Modes.STANDARD) {
            indexes = getIndexesOrDieTrying(name);
        } else {
            indexes = getIndexes(name);
            if (indexes == null) {
                return;
            }
        }
        for (Integer i : indexes) {
            if (value != null) {
                statement.setInt(i, value);
            } else {
                statement.setNull(i, java.sql.Types.INTEGER);
            }

        }
    }

    /**
     * Sets a parameter.
     *
     * @param mode
     *            values: STANDARD: same behaviour as normal method, QUIET: doesn't throw an exception if the parameter
     *            does not exist
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist and mode is set to STANDARD
     * 
     * @see PreparedStatement#setLong(int, long)
     */
    public void setLong(Modes mode, String name, Long value) throws SQLException {
        List<Integer> indexes = null;
        if (mode == Modes.STANDARD) {
            indexes = getIndexesOrDieTrying(name);
        } else {
            indexes = getIndexes(name);
            if (indexes == null) {
                return;
            }
        }
        for (Integer i : indexes) {
            if (value != null) {
                statement.setLong(i, value);
            } else {
                statement.setNull(i, java.sql.Types.BIGINT);
            }
        }
    }

    /**
     * Sets a parameter.
     *
     * @param mode
     *            values: STANDARD: same behaviour as normal method, QUIET: doesn't throw an exception if the parameter
     *            does not exist
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist and mode is set to STANDARD
     * 
     * @see PreparedStatement#setTimestamp(int, java.sql.Timestamp)
     */
    public void setTimestamp(Modes mode, String name, Timestamp value) throws SQLException {
        List<Integer> indexes = null;
        if (mode == Modes.STANDARD) {
            indexes = getIndexesOrDieTrying(name);
        } else {
            indexes = getIndexes(name);
            if (indexes == null) {
                return;
            }
        }
        for (Integer i : indexes) {
            if (value != null) {
                statement.setTimestamp(i, value);
            } else {
                statement.setNull(i, java.sql.Types.TIMESTAMP);
            }
        }
    }

    /**
     * Sets a parameter.
     *
     * @param mode
     *            values: STANDARD: same behaviour as normal method, QUIET: doesn't throw an exception if the parameter
     *            does not exist
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * 
     * @throws SQLException
     *             if an error occurred
     * @throws IllegalArgumentException
     *             if the parameter does not exist and mode is set to STANDARD
     * 
     * @see PreparedStatement#setDate(int, java.sql.Date)
     */
    public void setDate(Modes mode, String name, Date value) throws SQLException {
        List<Integer> indexes = null;
        if (mode == Modes.STANDARD) {
            indexes = getIndexesOrDieTrying(name);
        } else {
            indexes = getIndexes(name);
            if (indexes == null) {
                return;
            }
        }
        for (Integer i : indexes) {
            if (value != null) {
                statement.setDate(i, value);
            } else {
                statement.setNull(i, java.sql.Types.DATE);
            }
        }
    }

    /**
     * Returns the underlying statement.
     *
     * @return the statement
     */
    public PreparedStatement getStatement() {
        return statement;
    }

    /**
     * Executes the statement.
     *
     * @return true if the first result is a {@link ResultSet}
     * 
     * @throws SQLException
     *             if an error occurred
     * 
     * @see PreparedStatement#execute()
     */
    public boolean execute() throws SQLException {
        return statement.execute();
    }

    /**
     * Executes the statement, which must be a query.
     *
     * @return the query results
     * 
     * @throws SQLException
     *             if an error occurred
     * 
     * @see PreparedStatement#executeQuery()
     */
    public ResultSet executeQuery() throws SQLException {
        return statement.executeQuery();
    }

    /**
     * Executes the statement, which must be an SQL INSERT, UPDATE or DELETE statement; or an SQL statement that returns
     * nothing, such as a DDL statement.
     *
     * @return number of rows affected
     * 
     * @throws SQLException
     *             if an error occurred
     * 
     * @see PreparedStatement#executeUpdate()
     */
    public int executeUpdate() throws SQLException {
        return statement.executeUpdate();
    }

    /**
     * Closes the statement.
     *
     * @throws SQLException
     *             if an error occurred
     * 
     * @see Statement#close()
     */
    public void close() throws SQLException {
        statement.close();
    }

    /**
     * Adds the current set of parameters as a batch entry.
     *
     * @throws SQLException
     *             if something went wrong
     */
    public void addBatch() throws SQLException {
        statement.addBatch();
    }

    /**
     * Executes all of the batched statements.
     *
     * See {@link Statement#executeBatch()} for details.
     *
     * @return update counts for each statement
     * 
     * @throws SQLException
     *             if something went wrong
     */
    public int[] executeBatch() throws SQLException {
        return statement.executeBatch();
    }
}
