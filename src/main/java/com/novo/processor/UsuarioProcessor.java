/*
 * Decompiled with CFR 0.151.
 * 
 * Could not load the following classes:
 *  com.novo.database.dbinterface
 *  org.apache.commons.logging.Log
 *  org.apache.commons.logging.LogFactory
 */
package com.novo.processor;

import com.novo.database.dbinterface;
import com.novo.utils.Constant;
import com.novo.utils.Utils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class UsuarioProcessor {
    private static Log log = LogFactory.getLog((String)(Process.class.getName() + "." + Constant.VERSION));
    Properties queries = Utils.getProperties(Constant.CONSTANTS_QUERIES);
    Properties p = Utils.getProperties(Constant.CONSTANTS_CONFIG);

    public UsuarioProcessor() throws IOException {
    }

    public ArrayList<String> getGeneral(dbinterface db) {
        ArrayList<String> lista = new ArrayList<String>();
        String query = this.queries.getProperty("SQL_QUERY_GENERAL");
        if ("SI".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ACTUAL")) && "NO".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ESPECIFICA"))) {
            query = query.replaceAll("QUERY_EXTRA", this.queries.getProperty("SQL_ADD_FECHA_ACTUAL"));
        } else if ("NO".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ACTUAL")) && "SI".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ESPECIFICA"))) {
            String fec = this.p.getProperty("FEC_ESPECIFICA");
            String subquery = this.queries.getProperty("SQL_ADD_FECHA_PUNTUAL").replaceAll("DATE", fec);
            query = query.replaceAll("QUERY_EXTRA", subquery);
        } else if ("NO".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ACTUAL")) && "NO".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ESPECIFICA"))) {
            query = query.replaceAll("QUERY_EXTRA", " ");
        } else if ("SI".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ACTUAL")) && "SI".equalsIgnoreCase(this.p.getProperty("SQL_FEC_ESPECIFICA"))) {
            query = query.replaceAll("QUERY_EXTRA", " ");
        }
        if (db.executeQuery(query) != 0) {
            lista.add("ERROR_BD");
            log.debug((Object)"MSG_ERROR_CONSULTA_GENERAL");
        } else {
            while (db.nextRecord()) {
                String datos = db.getFieldString("TRAMA");
                lista.add(datos);
            }
            db.closeResult();
        }
        return lista;
    }
}

