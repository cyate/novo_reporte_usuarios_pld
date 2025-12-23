package com.novo.database;

import com.novo.exceptions.DatabaseErrorException;
import com.novo.exceptions.InvalidStateException;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import javax.sql.DataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class dbinterface implements Cloneable {
    private static Log log = LogFactory.getLog(dbinterface.class.getName());
    private Connection conn;
    private Statement stmt;
    private PreparedStatement prepStmt;
    private long generatedKey;
    private String dbName;
    private ResultSet queryResults;
    private dbconfig d;
    private Hashtable record = new Hashtable();
    private Object field = new Object();
    private String fieldString = "";
    private String query = "";
    private String[] columnNames = null;
    public int rc = -2;
    public String msgErr = "";
    public String msgErr2 = "";
    public int rowsCount = -1;
    public String tquery = "";
    public ResultSetMetaData md = null;
    public int numCols = 0;
    public ArrayList colName = new ArrayList();

    public ResultSet getQueryResults() {
        return this.queryResults;
    }

    public void setQueryResults(ResultSet queryResults) {
        this.queryResults = queryResults;
    }

    public dbinterface(String pathConfig) {
        this.d = new dbconfig(pathConfig);
        if (this.d.dbStatus.equals("OK")) {
            this.rc = 0;
            log.debug("load config [" + pathConfig + "] sucess !");
        } else {
            this.rc = -1;
            this.msgErr = this.d.dbLoadConfigError;
            log.error("load config [" + pathConfig + "] ERROR [" + this.msgErr + "] !");
        }

    }

    public dbinterface(String path, String db) {
        log.debug("Incorrect call using dbpool...path[" + path + "]db[" + db + "]");
        this.d = new dbconfig(path, db);
        if (this.d.dbStatus.equals("OK")) {
            this.rc = 0;
            log.debug("load config [" + path + db + "] sucess !");
        } else {
            this.rc = -1;
            this.msgErr = this.d.dbLoadConfigError;
            log.error("load config [" + path + db + "] ERROR [" + this.msgErr + "] !");
        }

    }

    public dbinterface() {
        this.rc = 0;
    }

    public void dbreset() {
        this.rc = -1;
        this.msgErr = "";
        this.msgErr2 = "";
        this.rowsCount = -1;
        this.tquery = "";
        this.numCols = 0;
        this.colName.clear();
        this.query = "";
        this.columnNames = null;
    }

    public int dbinicNew(DataSource ds) {
        try {
            this.conn = ds.getConnection();
            this.rc = 0;
        } catch (Exception e) {
            String msgErr = e.getMessage();
            String msgErr2 = e.getLocalizedMessage();
            this.rc = -1;
            log.debug("ERROR TRY to using dbpool [" + ds + "]");
            log.error("ERROR[" + msgErr + "] !", e);
            log.error("ERROR[" + msgErr2 + "] !", e);
        }

        return this.rc;
    }

    public int dbinicNew(DataSource ds, boolean autocommit) {
        try {
            this.conn = ds.getConnection();
            this.conn.setAutoCommit(autocommit);
            this.rc = 0;
        } catch (Exception e) {
            String msgErr = e.getMessage();
            String msgErr2 = e.getLocalizedMessage();
            this.rc = -1;
            log.debug("ERROR TRY to using dbpool");
            log.error("ERROR[" + msgErr + "] !", e);
            log.error("ERROR[" + msgErr2 + "] !", e);
        }

        return this.rc;
    }

    public int dbinic() {
        String connURL = "";

        try {
            Class.forName(this.d.driverName);

            try {
                if (this.d.connTypeDrv.equals("1")) {
                    connURL = this.d.connInit + this.d.connHost + ":" + this.d.connPort + "/" + this.d.connDB + ":" + this.d.connParm + "user=" + this.d.connUser + ";password=" + this.d.connPassword;
                    this.conn = DriverManager.getConnection(connURL);
                } else {
                    connURL = this.d.connInit;
                    this.conn = DriverManager.getConnection(connURL, this.d.connUser, this.d.connPassword);
                    log.debug("connected to ->[" + connURL + "]");
                }

                log.debug("connected to ->[" + connURL + "]");
                this.rc = 0;
            } catch (Exception e) {
                String msgErr = e.getMessage();
                String msgErr2 = e.getLocalizedMessage();
                this.rc = -1;
                log.error("ERROR[" + msgErr + "] !", e);
                log.error("ERROR[" + msgErr2 + "] !", e);
                log.error("conn[" + connURL + "] !", e);
            }
        } catch (Exception e) {
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            this.rc = -2;
            log.error("ERROR[" + this.msgErr + "] !", e);
        }

        return this.rc;
    }

    public boolean dbIsConnected() {
        try {
            return this.conn.isClosed();
        } catch (SQLException var2) {
            return false;
        }
    }

    public int executeQuery(String query) {
        this.query = query;
        this.tquery = query.substring(0, query.indexOf(32)).toUpperCase();

        try {
            this.rowsCount = 0;
            this.stmt = this.conn.createStatement();
            if (!this.tquery.equals("SELECT")) {
                this.rowsCount = this.stmt.executeUpdate(query);
                this.rc = 0;
            } else {
                this.rc = 0;
                this.queryResults = this.stmt.executeQuery(query);
                this.rowsCount = 0;
                this.md = this.queryResults.getMetaData();
                this.numCols = this.md.getColumnCount();

                for(int i = 1; i <= this.numCols; ++i) {
                    this.colName.add(i - 1, this.md.getColumnName(i));
                }
            }
        } catch (Exception e) {
            this.rc = -1;
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("ERROR[" + this.msgErr + "] !", e);
            log.error("ERROR[" + this.msgErr + "] !", e);
            log.error("sql[" + query + "] !", e);
        }

        return this.rc;
    }

    public int executeQuery(String query, ArrayList<Object> parameters) {
        this.query = query;
        this.tquery = query.substring(0, query.indexOf(32)).toUpperCase();

        try {
            this.rowsCount = 0;
            this.prepStmt = this.conn.prepareStatement(query);
            int i = 0;

            for(int j = 1; i < parameters.size(); ++j) {
                Object param = parameters.get(i);
                if (param instanceof String) {
                    this.prepStmt.setString(j, (String)param);
                } else if (param instanceof Integer) {
                    this.prepStmt.setInt(j, Integer.parseInt(param.toString()));
                } else if (param instanceof Double) {
                    this.prepStmt.setDouble(j, (Double)param);
                } else if (param instanceof Boolean) {
                    this.prepStmt.setBoolean(j, (Boolean)param);
                } else if (param instanceof Long) {
                    this.prepStmt.setLong(j, (Long)param);
                } else if (param instanceof Short) {
                    this.prepStmt.setShort(j, (Short)param);
                } else if (param instanceof Float) {
                    this.prepStmt.setFloat(j, (Float)param);
                } else {
                    this.prepStmt.setObject(j, param);
                }

                ++i;
            }

            if (!this.tquery.equals("SELECT")) {
                this.rowsCount = this.prepStmt.executeUpdate();
                this.rc = 0;
            } else {
                this.rc = 0;
                this.queryResults = this.prepStmt.executeQuery();
                this.rowsCount = 0;
                this.md = this.queryResults.getMetaData();
                this.numCols = this.md.getColumnCount();

                for(int j = 1; j <= this.numCols; ++j) {
                    this.colName.add(j - 1, this.md.getColumnName(j));
                }
            }
        } catch (Exception e) {
            this.rc = -1;
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("ERROR[" + this.msgErr + "] !", e);
            log.error("ERROR[" + this.msgErr + "] !", e);
            log.error("sql[" + query + "] !", e);
        }

        return this.rc;
    }

    public boolean executeProcedure(String query) {
        boolean exe = false;

        try {
            this.rowsCount = 0;
            this.stmt = this.conn.createStatement();
            exe = this.stmt.execute(query);
            if (exe) {
                this.queryResults = this.stmt.getResultSet();
                this.md = this.queryResults.getMetaData();
                this.numCols = this.md.getColumnCount();

                for(int i = 1; i <= this.numCols; ++i) {
                    this.colName.add(i - 1, this.md.getColumnName(i));
                }
            }
        } catch (SQLException e) {
            this.msgErr = e.getMessage();
            log.error(" ERROR[" + this.msgErr + "] !", e);
            log.error("sql[" + query + "] !", e);
        }

        return exe;
    }

    public List<Object> executeProcedure(String sql, Object[] parametrosIn, int[] parametrosOut) {
        try {
            log.info("Ejecutando : " + sql);
            CallableStatement callStmt = this.conn.prepareCall(sql);
            int cont = 1;

            for(int i = 0; i < parametrosIn.length; ++i) {
                callStmt.setObject(cont, parametrosIn[i]);
                ++cont;
            }

            List<Integer> posicionOut = new ArrayList();

            for(int i = 0; i < parametrosOut.length; ++i) {
                callStmt.registerOutParameter(cont, parametrosOut[i]);
                posicionOut.add(cont);
                ++cont;
            }

            List<Object> lista = new ArrayList();
            callStmt.execute();

            for(Integer posicion : posicionOut) {
                lista.add(callStmt.getObject(posicion));
            }

            return lista;
        } catch (Exception ex) {
            log.error(ex.toString(), ex);
            return null;
        }
    }

    public boolean nextRecord() {
        boolean b = true;

        try {
            if (!this.queryResults.next()) {
                this.rc = 1;

                try {
                    this.queryResults.close();
                } catch (Exception e) {
                    this.msgErr = e.getMessage();
                    this.msgErr2 = e.getLocalizedMessage();
                }

                b = false;
            }
        } catch (Exception e) {
            b = false;
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("ERROR[" + this.msgErr + "] !", e);
            log.error("ERROR[" + this.msgErr + "] !", e);
        }

        return b;
    }

    public void closeResult() {
        this.dbClose();
    }

    public void dbClose() {
        try {
            if (this.queryResults != null) {
                this.queryResults.close();
            }
        } catch (RuntimeException e) {
            log.error("RuntimeException: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Exception: " + e.getMessage(), e);
        }

        try {
            if (this.stmt != null) {
                this.stmt.close();
            }
        } catch (RuntimeException e) {
            log.error("RuntimeException: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Exception: " + e.getMessage(), e);
        }

    }

    public Object getField(String name) {
        try {
            this.field = this.queryResults.getObject(name);
        } catch (Exception e) {
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("getField[" + name + "] [" + this.msgErr + "][" + this.msgErr2 + "]", e);
        }

        return this.field;
    }

    public String getFieldString(String name) {
        try {
            this.fieldString = this.queryResults.getString(name);
        } catch (Exception e) {
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("Exception getFieldString[" + name + "] [" + this.msgErr + "][" + this.msgErr2 + "]", e);
        }

        if (this.fieldString == null || this.fieldString.equalsIgnoreCase("null")) {
            this.fieldString = "";
        }

        return this.fieldString;
    }

    public byte[] getFieldBlob(String name) {
        byte[] b = null;

        try {
            Blob blob = this.queryResults.getBlob(name);
            b = blob.getBytes(1L, (int)blob.length());
        } catch (Exception e) {
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("getFieldString[" + name + "] [" + this.msgErr + "][" + this.msgErr2 + "]", e);
        }

        return b;
    }

    public Hashtable getRecord() {
        this.record.clear();

        try {
            if (this.queryResults.next()) {
                ++this.rowsCount;
                log.debug(" [queryResults.next] !");

                for(int i = 0; i < this.numCols; ++i) {
                    this.record.put(this.md.getColumnLabel(i + 1), (String)this.queryResults.getObject(i + 1));
                }

                this.rc = 0;
            } else {
                this.rc = 1;

                try {
                    this.queryResults.close();
                } catch (Exception e) {
                    this.msgErr = e.getMessage();
                    this.msgErr2 = e.getLocalizedMessage();
                }
            }
        } catch (Exception e) {
            this.rc = -1;
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("ERROR[" + this.msgErr + "] !", e);
            log.error("ERROR[" + this.msgErr2 + "] !", e);

            try {
                this.queryResults.close();
            } catch (Exception var3) {
            }
        }

        return this.record;
    }

    public int dbend() {
        String dbname = "";

        try {
            log.info("dbend  stmt[" + this.stmt + "] conn[" + this.conn + "]conectado [" + (this.conn != null && this.conn.isClosed()) + "]");
            if (this.conn != null && !this.conn.isClosed()) {
                dbname = this.conn.getMetaData().getDatabaseProductName();
                if (this.queryResults != null) {
                    log.info("close db queryResult [" + dbname + "]!");
                    this.queryResults.close();
                }

                if (this.stmt != null) {
                    log.info("close db statement [" + dbname + "]!");
                    this.stmt.close();
                }

                this.rc = 0;
            }
        } catch (RuntimeException e) {
            log.error("RuntimeException: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Exception: " + e.getMessage(), e);
            this.rc = -1;
            this.msgErr = e.getMessage();
            this.msgErr2 = e.getLocalizedMessage();
            log.error("dbend.Exception(" + dbname + ") [" + this.msgErr + "] [" + this.msgErr2 + "] (stmt)!");
        } finally {
            try {
                this.rc = 0;
                if (this.conn != null) {
                    log.info("close db connection [" + dbname + "]!");
                    this.conn.close();
                    log.info("finalizando dbend coneccion cerrada [" + this.conn.isClosed() + "]");
                }
            } catch (Exception e) {
                this.rc = -1;
                this.msgErr = e.getMessage();
                this.msgErr2 = e.getLocalizedMessage();
                log.error("dbend.Exception(" + dbname + ") [" + this.msgErr + "] [" + this.msgErr2 + "] (conn)!");
            }

        }

        return this.rc;
    }

    public void testinterface() {
        dbinterface data = new dbinterface("oracle.properties");
        if (data.rc == 0 && data.dbinic() == 0) {
            if (data.executeQuery("select * from CONFIG_PRODUCTOS where prefix = 'B'") == 0) {
                new Hashtable();

                for(Hashtable r = data.getRecord(); data.rc == 0; r = data.getRecord()) {
                    String cName = "";

                    for(int i = 0; i < data.numCols; ++i) {
                        log.debug(data.colName.get(i) + "=" + r.get(data.colName.get(i)));
                    }
                }
            }

            data.dbend();
        }

    }

    public void logRec1() {
        String cName = "";

        for(int i = 0; i < this.numCols; ++i) {
            try {
                log.debug(this.colName.get(i) + "=" + (String)this.queryResults.getObject((String)this.colName.get(i)));
            } catch (Exception e1) {
                log.debug("error: [" + (String)this.colName.get(i) + "]" + e1.getMessage() + " - " + e1.getLocalizedMessage());
            }
        }

    }

    public Savepoint setSaveponint(String name) {
        Savepoint s = null;

        try {
            s = this.conn.setSavepoint(name);
        } catch (SQLException e) {
            log.debug("SQLException caught: " + e.getMessage());
        }

        return s;
    }

    public void beginTransaction() throws DatabaseErrorException {
        try {
            this.conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public Savepoint setSavepoint(String name) throws DatabaseErrorException, InvalidStateException {
        try {
            if (this.conn.getAutoCommit()) {
                throw new InvalidStateException("Not in a transaction.");
            } else {
                return this.conn.setSavepoint(name);
            }
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public Savepoint setSavepoint() throws DatabaseErrorException, InvalidStateException {
        try {
            if (this.conn.getAutoCommit()) {
                throw new InvalidStateException("Not in a transaction.");
            } else {
                return this.conn.setSavepoint();
            }
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public void releaseSavepoint(Savepoint savepoint) throws DatabaseErrorException, InvalidStateException {
        try {
            if (this.conn.getAutoCommit()) {
                throw new InvalidStateException("Not in a transaction.");
            } else {
                this.conn.releaseSavepoint(savepoint);
            }
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public void rollback() throws DatabaseErrorException, InvalidStateException {
        try {
            if (this.conn.getAutoCommit()) {
                throw new InvalidStateException("Not in a transaction.");
            } else {
                this.conn.rollback();
                this.conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public void rollback(Savepoint savepoint) throws DatabaseErrorException, InvalidStateException {
        try {
            if (this.conn.getAutoCommit()) {
                throw new InvalidStateException("Not in a transaction.");
            } else {
                this.conn.rollback(savepoint);
            }
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public void commit() throws DatabaseErrorException, InvalidStateException {
        try {
            if (this.conn.getAutoCommit()) {
                throw new InvalidStateException("Not in a transaction.");
            } else {
                this.conn.commit();
                this.conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public void endTransaction() throws InvalidStateException, DatabaseErrorException {
        try {
            if (this.conn.getAutoCommit()) {
                throw new InvalidStateException("Not in a transaction.");
            } else {
                this.conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseErrorException(e);
        }
    }

    public Connection getConn() {
        return this.conn;
    }

    public boolean eliminarRegistros(String sql) {
        log.info("Eliminando Registro->[" + sql + "]");
        this.dbreset();
        if (this.executeQuery(sql) != 0) {
            log.info("Error eliminando registro");
            this.dbClose();
            return false;
        } else {
            this.dbClose();
            return true;
        }
    }

    public void aplicarRollsback(ArrayList<String> lista) {
        String sql = "";
        log.info("Aplicando RollsBack...");
        Iterator<String> i = lista.iterator();

        while(i.hasNext()) {
            log.info("Sql [" + sql + "]");
            sql = (String)i.next();
            this.eliminarRegistros(sql);
        }

    }

    public Object clone() throws CloneNotSupportedException {
        dbinterface obj = null;

        try {
            obj = (dbinterface)super.clone();
        } catch (CloneNotSupportedException ex) {
            ex.printStackTrace();
            log.error(" no se puede duplicar");
        }

        return obj;
    }

    public String getFieldValue(String column) {
        try {
            return this.colName.contains(column) ? this.getFieldString(column) : "";
        } catch (Exception e) {
            log.debug("error: " + e.getMessage() + " - " + e.getLocalizedMessage());
            return "";
        }
    }

    private ArrayList<HashMap> resultSetToArrayList(ResultSet rs) throws SQLException {
        log.info("Iniciando conversion de resultSet a Hash");
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        ArrayList<HashMap> results = new ArrayList();

        while(rs.next()) {
            HashMap row = new HashMap();

            for(int i = 1; i <= columns; ++i) {
                if (!md.getColumnName(i).equals("GENERATED_KEY") && !md.getTableName(i).equals("")) {
                    row.put(md.getTableName(i) + md.getColumnName(i).toUpperCase(), rs.getObject(i) == null ? "" : rs.getObject(i));
                } else {
                    row.put(md.getTableName(i) + md.getColumnName(i).toUpperCase(), rs.getObject(i) == null ? "" : rs.getObject(i));
                }
            }

            results.add(row);
        }

        log.info("Fin de conversion de resultSet a Hash");
        return results;
    }

    public ArrayList<HashMap> ejecutarQueryToHash(String query) {
        ArrayList<HashMap> list = new ArrayList();
        log.info("Iniciando ejecucion de query: " + query);

        try {
            this.queryResults = this.conn.prepareStatement(query).executeQuery();
            list = this.resultSetToArrayList(this.queryResults);
            log.info("Query ejecutado");
        } catch (Exception e) {
            log.error("ERROR: ejecutarQueryToHash(String query)", e);
        }

        return list;
    }
}
