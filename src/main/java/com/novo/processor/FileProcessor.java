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
import com.novo.processor.UsuarioProcessor;
import com.novo.utils.Constant;
import com.novo.utils.Utils;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileProcessor {
    private static Log log = LogFactory.getLog((String)(Process.class.getName() + "." + Constant.VERSION));
    Properties pconfig = Utils.getProperties(Constant.CONSTANTS_CONFIG);
    Properties pqueries = Utils.getProperties(Constant.CONSTANTS_QUERIES);

    public FileProcessor() throws IOException {
    }

    public ArrayList<String> createFile(dbinterface db) {
        ArrayList<String> lista = null;
        ArrayList datos = new ArrayList();
        try {
            UsuarioProcessor user = new UsuarioProcessor();
            lista = user.getGeneral(db);
            if (!lista.isEmpty() && "ERROR_BD".equalsIgnoreCase(lista.get(0))) {
                return lista;
            }
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter((OutputStream)new FileOutputStream(this.pconfig.getProperty("FILE_PATH").trim() + this.pconfig.getProperty("FILE_NAME").trim() + Utils.getDateFile() + this.pconfig.getProperty("FILE_EXT").trim()), "ISO-8859-1"));
            String data_row = "";
            int generalcount = lista.size();
            String cabecera = this.pconfig.getProperty("FILED_CABECERA");
            if (generalcount == 0) {
                if ("SI".equalsIgnoreCase(this.pconfig.getProperty("CABECERA_VACIO"))) {
                    data_row = cabecera;
                    data_row = data_row + "\r\n";
                    out.write(data_row);
                }
            } else {
                data_row = cabecera;
                data_row = data_row + "\r\n";
                out.write(data_row);
            }
            Iterator<String> iterator = lista.iterator();
            while (iterator.hasNext()) {
                String general;
                String valor = general = iterator.next();
                String concatenado = "";
                String[] info = valor.split("\\|");
                String fecha = info[8];
                String strDateFormat = "dd-MM-yy";
                Date objSDF = new SimpleDateFormat(strDateFormat).parse(fecha);
                System.out.println("FECHA 1: " + objSDF);
                String strDateFormat2 = "dd/MM/yyyy";
                SimpleDateFormat objSDF2 = new SimpleDateFormat(strDateFormat2);
                System.out.println("FECHA TRANS: " + objSDF2.format(objSDF));
                for (int i = 0; i < info.length; ++i) {
                    concatenado = i == 8 ? concatenado + objSDF2.format(objSDF) + "|" : (i < info.length - 1 ? concatenado + info[i] + "|" : concatenado + info[i]);
                }
                data_row = concatenado;
                log.info("-------------------------------Resultado-------------------------------");
                log.info(data_row);
                data_row = data_row + "\r\n";
                out.write(data_row);
            }
            out.close();
            return lista;
        }
        catch (Exception e) {
            e.printStackTrace();
            return lista;
        }
    }
}

