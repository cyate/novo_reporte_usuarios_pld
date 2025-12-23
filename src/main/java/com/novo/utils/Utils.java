/*
 * Decompiled with CFR 0.151.
 * 
 * Could not load the following classes:
 *  javax.xml.bind.DatatypeConverter
 *  org.apache.commons.logging.Log
 *  org.apache.commons.logging.LogFactory
 */
package com.novo.utils;

import com.novo.utils.Constant;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Utils {
    private static Log log = LogFactory.getLog((String)(Utils.class.getName() + "." + Constant.VERSION));
    private static final String MSG_ERROR_CONSULTA = "Error al ejecutar query";

    public static Properties getProperties(String nameFile) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(nameFile));
        }
        catch (FileNotFoundException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
        }
        return properties;
    }

    public static String obtenerHora() {
        String fechaActual = "";
        SimpleDateFormat formato = new SimpleDateFormat("HH:MM");
        Date hoy = new Date();
        fechaActual = formato.format(hoy);
        return fechaActual;
    }

    public static String getDate() {
        String fechaActual = "";
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd");
        Date hoy = new Date();
        fechaActual = formato.format(hoy);
        return fechaActual;
    }

    public static String getDateFile() {
        String fechaActual = "";
        SimpleDateFormat formato = new SimpleDateFormat("yyyyMMdd");
        Date hoy = new Date();
        fechaActual = formato.format(hoy);
        String fecha = fechaActual.substring(6, 8) + "_" + fechaActual.substring(4, 6) + "_" + fechaActual.substring(0, 4);
        return fecha;
    }

    public static String completar(String cadena, int count) {
        int r = count - cadena.length();
        String result = "";
        for (int i = 0; i < r; ++i) {
            result = result + "0";
        }
        return result + cadena;
    }

    public static String completarString(String cadena, int count) {
        int r = count - cadena.length();
        String result = "";
        for (int i = 0; i < r; ++i) {
            result = result + " ";
        }
        return cadena + result;
    }

    public static String truncarString(String cadena, int count) {
        return cadena.substring(0, count);
    }

    private static String decrypt(String text) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException, IOException {
        String key = "novopayment02017";
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
        cipher.init(2, secretKey);
        byte[] cipherText = DatatypeConverter.parseBase64Binary((String)text);
        byte[] doFinal = cipher.doFinal(cipherText);
        String decryptedString = new String(doFinal, "UTF-8");
        return decryptedString;
    }

    private static String encrypt(String text) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException, IOException {
        String key = "novopayment02017";
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
        cipher.init(1, secretKey);
        byte[] cipherText = cipher.doFinal(text.getBytes());
        String printBase64Binary = DatatypeConverter.printBase64Binary((byte[])cipherText);
        return printBase64Binary;
    }

    public static String getCondition(ArrayList<String> lista) {
        String cond = "";
        for (String dato : lista) {
            String[] valor = dato.split("&");
            String[] v = valor[0].split("-");
            cond = cond + v[0];
            cond = cond + ",";
        }
        log.debug((Object)("Condition: " + cond));
        return cond.substring(0, cond.length() - 1);
    }

    public static String getConditionDetail(String lista) {
        String[] valor = lista.split("&");
        String[] v = valor[0].split("-");
        log.debug((Object)("Condition: " + v[1]));
        return v[1];
    }

    public static String[] getData(String lista) {
        String[] v = lista.split("&");
        try {
            Properties process = Utils.getProperties(Constant.CONSTANTS_CONFIG);
            String[] valor = lista.split("&");
            v = valor[1].split("\\" + process.getProperty("FIELD_SEPARATOR"));
            log.debug((Object)("Condition: " + v[1]));
            return v;
        }
        catch (Exception e) {
            e.printStackTrace();
            return v;
        }
    }

    public static String getId(String lista) {
        String[] valor = lista.split("&");
        String[] v = valor[0].split("-");
        log.debug((Object)("Condition: " + v[0]));
        return v[0];
    }
}

