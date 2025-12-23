/*
 * Decompiled with CFR 0.151.
 * 
 * Could not load the following classes:
 *  com.novo.database.dbinterface
 *  org.apache.commons.logging.Log
 *  org.apache.commons.logging.LogFactory
 */
package com.novo.main;

import com.novo.database.dbinterface;
import com.novo.main.SendMail;
import com.novo.processor.FileProcessor;
import com.novo.utils.Constant;
import com.novo.utils.Utils;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Main {
    private static Log log = LogFactory.getLog((String)(Main.class.getName() + "." + Constant.VERSION));
    private static dbinterface dbo = null;

    public static void main(String[] args) {
        log.info((Object)("Program Name [" + Constant.PROGRAM + "]   Version [" + Constant.VERSION + "]"));
        try {
            log.info((Object)"-----------------------Properties--------------------------");
            Properties p = Utils.getProperties(Constant.CONSTANTS_CONFIG);
            Properties pemail = Utils.getProperties(Constant.CONSTANTS_MAIL);
            File file = new File(p.getProperty("FILE_PATH").trim() + p.getProperty("FILE_NAME").trim() + Utils.getDateFile() + p.getProperty("FILE_EXT").trim());
            log.info((Object)("-----------Path de creacion: " + file.getPath()));
            log.info((Object)"--------------------Open BD Conection----------------------");
            dbo = new dbinterface(Constant.ORACLE_BD);
            if (Main.dbo.rc != 0) {
                log.info((Object)"Configuraci\u00f3n de BD ORACLE no disponible. Proceso cancelado");
                Main.enviarAlerta(pemail);
                System.exit(0);
                return;
            }
            if (dbo.dbinic() != 0) {
                log.error((Object)"Error en conexion al ambiente de datos Oracle");
                Main.enviarAlerta(pemail);
                System.exit(0);
            } else {
                log.info((Object)"----------------------File Processor----------------------");
                FileProcessor fp = new FileProcessor();
                ArrayList<String> lista = fp.createFile(dbo);
                if (!lista.isEmpty() && "ERROR_BD".equalsIgnoreCase(lista.get(0))) {
                    Main.enviarAlerta(pemail);
                } else {
                    int datos = 0;
                    if (!lista.isEmpty()) {
                        datos = lista.size();
                    }
                    log.info((Object)("--Cantidad de Usuarios Consultados: " + datos + "  ---------------------"));
                    log.info((Object)"--------------------Notificaciones----------------------");
                    Main.enviarNotificaciones(pemail, datos);
                }
            }
            dbo.dbend();
            log.info((Object)("End Program Name [" + Constant.PROGRAM + "]"));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void enviarNotificaciones(Properties prop, int datos) {
        Date objDate = new Date();
        String strDateFormat = "dd/MM/yyyy HH:mm:ss";
        SimpleDateFormat objSDF = new SimpleDateFormat(strDateFormat);
        String smtpServ = prop.getProperty("smtpConfName");
        String sender = prop.getProperty("smtpConfSender");
        String mailUser = prop.getProperty("mailUser");
        String mailPasswd = prop.getProperty("mailPasswd");
        String asuntoMail = prop.getProperty("asuntoMail");
        int numAdr = Integer.valueOf(prop.getProperty("smtpNumberAdresses"));
        String[] recipients = new String[100];
        for (int i = 0; i < numAdr; ++i) {
            recipients[i] = prop.getProperty("smtpRecvAddress" + (i + 1));
        }
        String mensaje = "<style>.w {font-size:10.0pt;font-family:Verdana,sans-serif;padding:3.0pt 3.0pt 3.0pt 3.0pt;}th {font-size:10.5pt;font-family:Verdana,sans-serif;color:#EDEDED;background:#13469C;padding:3.0pt 3.0pt 3.0pt 3.0pt;}</style>";
        mensaje = mensaje + "</br></br>Fecha/hora: " + objSDF.format(objDate) + "</br>";
        mensaje = mensaje + "</br>Se notifica el resumen del proceso:</br>";
        mensaje = mensaje + "</br>Nro de usuarios enviados en el archivo: " + datos + "</br>";
        log.info((Object)mensaje);
        SendMail.sendEmailHtml(smtpServ, recipients, asuntoMail, mensaje, sender, mailUser, mailPasswd, prop.getProperty("IMAGE_PATH"));
    }

    public static void enviarAlerta(Properties prop) {
        Date objDate = new Date();
        String strDateFormat = "dd/MM/yyyy HH:mm:ss";
        SimpleDateFormat objSDF = new SimpleDateFormat(strDateFormat);
        String smtpServ = prop.getProperty("smtpConfName");
        String sender = prop.getProperty("smtpConfSender");
        String mailUser = prop.getProperty("mailUser");
        String mailPasswd = prop.getProperty("mailPasswd");
        String asuntoMail = prop.getProperty("asuntoMailAlert");
        int numAdr = Integer.valueOf(prop.getProperty("smtpNumberAdressesAlert"));
        String[] recipients = new String[100];
        for (int i = 0; i < numAdr; ++i) {
            recipients[i] = prop.getProperty("smtpRecvAddressAlert" + (i + 1));
        }
        String alerta = prop.getProperty("msj_alert_bd");
        String mensaje = "<style>.w {font-size:10.0pt;font-family:Verdana,sans-serif;padding:3.0pt 3.0pt 3.0pt 3.0pt;}th {font-size:10.5pt;font-family:Verdana,sans-serif;color:#EDEDED;background:#13469C;padding:3.0pt 3.0pt 3.0pt 3.0pt;}</style>";
        mensaje = mensaje + "</br></br>Fecha/hora: " + objSDF.format(objDate) + "</br>";
        mensaje = mensaje + "</br>Se notifica que se presentaron fallas en el proceso correspondiente.</br>";
        mensaje = mensaje + "</br><b>Observaciones:</b></br>";
        mensaje = mensaje + "</br><table border=\"1\" cellspacing=\"0\" cellpadding=\"0\"><tr><th style=\"width: 450px;\">Detalle</th></tr>";
        mensaje = mensaje + "<tr><td style=\"width: 450px;\">";
        mensaje = mensaje + "&nbsp;" + alerta.replace("\u00f3", "&oacute;").replace("\u00e1", "&aacute;").replace("\u00e9", "&eacute;").replace("\u00ed", "&iacute;").replace("\u00fa", "&uacute;");
        mensaje = mensaje + "</td></tr>";
        SendMail.sendEmailHtml(smtpServ, recipients, asuntoMail, mensaje, sender, mailUser, mailPasswd, prop.getProperty("IMAGE_PATH"));
    }
}

