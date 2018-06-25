package org.sqlite.jdbc3;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteOpenMode;

public abstract class JDBC3Connection
        extends SQLiteConnection
{
    private final AtomicInteger savePoint = new AtomicInteger(0);
    private Map<String, Class<?>> typeMap;

    private boolean firstStatementWasExecuted = false;
    private boolean readOnly = false;
    private TransactionMode currentTransactionType;

    protected JDBC3Connection(String url, String fileName, Properties prop)
            throws SQLException
    {
        super(url, fileName, prop);
        this.currentTransactionType = this.getDatabase().getConfig().getTransactionMode();
    }

    public void setFirstStatementWasExecuted(final boolean firstStatementWasExecuted) {
        this.firstStatementWasExecuted = firstStatementWasExecuted;
    }

    public boolean isFirstStatementWasExecuted() {
        return firstStatementWasExecuted;
    }

    public TransactionMode getCurrentTransactionType(){
        return this.currentTransactionType;
    }

    public void setCurrentTransactionType(final TransactionMode currentTransactionType) {
        this.currentTransactionType = currentTransactionType;
    }

    public void checkTransactionMode() throws SQLException {
        if(this.getDatabase().getConfig().isExplicitReadOnlyEnabled()){
            if(this.isReadOnly()){
                // this is a read-only transaction, make sure all writing operations are rejected by the DB
                // (note: this pragma is evaluated on a per-transaction basis by SQLite)
                this.getDatabase()._exec("PRAGMA read_only = true;");
            }else{
                if(this.getCurrentTransactionType() == TransactionMode.DEFFERED){
                    if(this.isFirstStatementWasExecuted()){
                        // first statement was already executed; cannot upgrade to write transaction!
                        throw new SQLException("A statement has already been executed on this connection; cannot upgrade to write transaction!");
                    }else{
                        // this is the first statement in the transaction; close and create an immediate one
                        this.getDatabase()._exec("ROLLBACK;");
                        // start the write transaction
                        this.getDatabase()._exec("BEGIN IMMEDIATE;");
                        this.getDatabase()._exec("PRAGMA read_only = false;");
                        this.setCurrentTransactionType(TransactionMode.IMMEDIATE);
                    }
                }

            }
        }
    }

    @Override
    public void commit() throws SQLException {
        super.commit();
        this.firstStatementWasExecuted = false;
    }

    @Override
    public void rollback() throws SQLException {
        super.rollback();
        this.firstStatementWasExecuted = false;
    }

    /**
     * @see java.sql.Connection#getCatalog()
     */
    public String getCatalog()
            throws SQLException
    {
        checkOpen();
        return null;
    }

    /**
     * @see java.sql.Connection#setCatalog(java.lang.String)
     */
    public void setCatalog(String catalog)
            throws SQLException
    {
        checkOpen();
    }

    /**
     * @see java.sql.Connection#getHoldability()
     */
    public int getHoldability()
            throws SQLException
    {
        checkOpen();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    /**
     * @see java.sql.Connection#setHoldability(int)
     */
    public void setHoldability(int h)
            throws SQLException
    {
        checkOpen();
        if (h != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
            throw new SQLException("SQLite only supports CLOSE_CURSORS_AT_COMMIT");
        }
    }

    /**
     * @see java.sql.Connection#getTypeMap()
     */
    public Map<String, Class<?>> getTypeMap()
            throws SQLException
    {
        synchronized (this) {
            if (this.typeMap == null) {
                this.typeMap = new HashMap<String, Class<?>>();
            }

            return this.typeMap;
        }
    }

    /**
     * @see java.sql.Connection#setTypeMap(java.util.Map)
     */
    public void setTypeMap(Map map)
            throws SQLException
    {
        synchronized (this) {
            this.typeMap = map;
        }
    }

    /**
     * @see java.sql.Connection#isReadOnly()
     */
    public boolean isReadOnly() throws SQLException {
        SQLiteConfig config = getDatabase().getConfig();
        return (
                // the entire database is read-only
                ((config.getOpenModeFlags() & SQLiteOpenMode.READONLY.flag) != 0)
                // the flag was set explicitly by the user on this connection
                || (config.isExplicitReadOnlyEnabled() && this.readOnly)
        );
    }

    /**
     * @see java.sql.Connection#setReadOnly(boolean)
     */
    public void setReadOnly(boolean ro) throws SQLException {
        if(this.getDatabase().getConfig().isExplicitReadOnlyEnabled()){
            if(ro != this.readOnly && this.firstStatementWasExecuted){
                throw new SQLException("Cannot change Read-Only status of this connection: the first statement was" +
                    " already executed and the transaction is open.");
            }
        }else{
            // trying to change read-only flag
            if (ro != isReadOnly()) {
                throw new SQLException(
                    "Cannot change read-only flag after establishing a connection." +
                        " Use SQLiteConfig#setReadOnly and SQLiteConfig.createConnection().");
            }
        }
        this.readOnly = ro;
    }

    /**
     * @see java.sql.Connection#nativeSQL(java.lang.String)
     */
    public String nativeSQL(String sql)
    {
        return sql;
    }

    /**
     * @see java.sql.Connection#clearWarnings()
     */
    public void clearWarnings()
            throws SQLException
    {
    }

    /**
     * @see java.sql.Connection#getWarnings()
     */
    public SQLWarning getWarnings()
            throws SQLException
    {
        return null;
    }

    /**
     * @see java.sql.Connection#createStatement()
     */
    public Statement createStatement()
            throws SQLException
    {
        return createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
                ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /**
     * @see java.sql.Connection#createStatement(int, int)
     */
    public Statement createStatement(int rsType, int rsConcurr)
            throws SQLException
    {
        return createStatement(rsType, rsConcurr, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /**
     * @see java.sql.Connection#createStatement(int, int, int)
     */
    public abstract Statement createStatement(int rst, int rsc, int rsh)
            throws SQLException;

    /**
     * @see java.sql.Connection#prepareCall(java.lang.String)
     */
    public CallableStatement prepareCall(String sql)
            throws SQLException
    {
        return prepareCall(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
                ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /**
     * @see java.sql.Connection#prepareCall(java.lang.String, int, int)
     */
    public CallableStatement prepareCall(String sql, int rst, int rsc)
            throws SQLException
    {
        return prepareCall(sql, rst, rsc, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /**
     * @see java.sql.Connection#prepareCall(java.lang.String, int, int, int)
     */
    public CallableStatement prepareCall(String sql, int rst, int rsc, int rsh)
            throws SQLException
    {
        throw new SQLException("SQLite does not support Stored Procedures");
    }

    /**
     * @see java.sql.Connection#prepareStatement(java.lang.String)
     */
    public PreparedStatement prepareStatement(String sql)
            throws SQLException
    {
        return prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    }

    /**
     * @see java.sql.Connection#prepareStatement(java.lang.String, int)
     */
    public PreparedStatement prepareStatement(String sql, int autoC)
            throws SQLException
    {
        return prepareStatement(sql);
    }

    /**
     * @see java.sql.Connection#prepareStatement(java.lang.String, int[])
     */
    public PreparedStatement prepareStatement(String sql, int[] colInds)
            throws SQLException
    {
        return prepareStatement(sql);
    }

    /**
     * @see java.sql.Connection#prepareStatement(java.lang.String, java.lang.String[])
     */
    public PreparedStatement prepareStatement(String sql, String[] colNames)
            throws SQLException
    {
        return prepareStatement(sql);
    }

    /**
     * @see java.sql.Connection#prepareStatement(java.lang.String, int, int)
     */
    public PreparedStatement prepareStatement(String sql, int rst, int rsc)
            throws SQLException
    {
        return prepareStatement(sql, rst, rsc, ResultSet.CLOSE_CURSORS_AT_COMMIT);
    }

    /**
     * @see java.sql.Connection#prepareStatement(java.lang.String, int, int, int)
     */
    public abstract PreparedStatement prepareStatement(String sql, int rst, int rsc, int rsh)
            throws SQLException;

    /**
     * @see java.sql.Connection#setSavepoint()
     */
    public Savepoint setSavepoint()
            throws SQLException
    {
        checkOpen();
        if(getAutoCommit()) {
            // when a SAVEPOINT is the outermost savepoint and not
            // with a BEGIN...COMMIT then the behavior is the same
            // as BEGIN DEFERRED TRANSACTION
            // http://www.sqlite.org/lang_savepoint.html
            getConnectionConfig().setAutoCommit(false);
        }
        Savepoint sp = new JDBC3Savepoint(savePoint.incrementAndGet());
        getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()), false);
        return sp;
    }

    /**
     * @see java.sql.Connection#setSavepoint(java.lang.String)
     */
    public Savepoint setSavepoint(String name)
            throws SQLException
    {
        checkOpen();
        if(getAutoCommit()) {
            // when a SAVEPOINT is the outermost savepoint and not
            // with a BEGIN...COMMIT then the behavior is the same
            // as BEGIN DEFERRED TRANSACTION
            // http://www.sqlite.org/lang_savepoint.html
            getConnectionConfig().setAutoCommit(false);
        }
        Savepoint sp = new JDBC3Savepoint(savePoint.incrementAndGet(), name);
        getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()), false);
        return sp;
    }

    /**
     * @see java.sql.Connection#releaseSavepoint(java.sql.Savepoint)
     */
    public void releaseSavepoint(Savepoint savepoint)
            throws SQLException
    {
        checkOpen();
        if (getAutoCommit()) {
            throw new SQLException("database in auto-commit mode");
        }
        getDatabase().exec(String.format("RELEASE SAVEPOINT %s", savepoint.getSavepointName()), false);
    }

    /**
     * @see java.sql.Connection#rollback(java.sql.Savepoint)
     */
    public void rollback(Savepoint savepoint)
            throws SQLException
    {
        checkOpen();
        if (getAutoCommit()) {
            throw new SQLException("database in auto-commit mode");
        }
        getDatabase().exec(String.format("ROLLBACK TO SAVEPOINT %s", savepoint.getSavepointName()), getAutoCommit());
    }

    // UNUSED FUNCTIONS /////////////////////////////////////////////

    public Struct createStruct(String t, Object[] attr)
            throws SQLException
    {
        throw new SQLException("unsupported by SQLite");
    }


}
