/*
 * Decompiled with CFR 0.151.
 * 
 * Could not load the following classes:
 *  javax.activation.DataHandler
 *  javax.activation.DataSource
 *  javax.activation.FileDataSource
 *  javax.mail.Address
 *  javax.mail.BodyPart
 *  javax.mail.Message
 *  javax.mail.Message$RecipientType
 *  javax.mail.Multipart
 *  javax.mail.Session
 *  javax.mail.Transport
 *  javax.mail.internet.InternetAddress
 *  javax.mail.internet.MimeBodyPart
 *  javax.mail.internet.MimeMessage
 *  javax.mail.internet.MimeMultipart
 */
package com.novo.main;

import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class SendMail {
    public static void sendEmail(String smtpServ, String[] recipients, String subject, String message, String from, String user, String password) {
        boolean debug = true;
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpServ);
            Session session = Session.getDefaultInstance((Properties)props, null);
            session.setDebug(debug);
            MimeMessage msg = new MimeMessage(session);
            InternetAddress addressFrom = new InternetAddress(from);
            msg.setFrom((Address)addressFrom);
            int i = 0;
            while (recipients[i] != null) {
                msg.setRecipient(Message.RecipientType.TO, (Address)new InternetAddress(recipients[i]));
                msg.addHeader("MyHeaderName", "myHeaderValue");
                msg.setSubject(subject);
                msg.setContent((Object)message, "text/plain");
                Transport tr = session.getTransport("smtp");
                tr.connect(smtpServ, user, password);
                Transport.send((Message)msg);
                ++i;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendEmailHtml(String smtpServ, String[] recipients, String subject, String message, String from, String user, String password, String rutaImg) {
        boolean debug = true;
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpServ);
            Session session = Session.getDefaultInstance((Properties)props, null);
            session.setDebug(debug);
            MimeMessage msg = new MimeMessage(session);
            InternetAddress addressFrom = new InternetAddress(from);
            msg.setFrom((Address)addressFrom);
            msg.setSubject(subject);
            int i = 0;
            while (recipients[i] != null) {
                msg.setRecipient(Message.RecipientType.TO, (Address)new InternetAddress(recipients[i]));
                MimeMultipart multipart = new MimeMultipart("related");
                MimeBodyPart messageBodyPart = new MimeBodyPart();
                String htmlText = "<img src=\"cid:image\">" + message;
                messageBodyPart.setContent((Object)htmlText, "text/html");
                multipart.addBodyPart((BodyPart)messageBodyPart);
                messageBodyPart = new MimeBodyPart();
                FileDataSource fds = new FileDataSource(rutaImg);
                messageBodyPart.setDataHandler(new DataHandler((DataSource)fds));
                messageBodyPart.setHeader("Content-ID", "<image>");
                multipart.addBodyPart((BodyPart)messageBodyPart);
                msg.setContent((Multipart)multipart);
                Transport tr = session.getTransport("smtp");
                tr.connect(smtpServ, user, password);
                Transport.send((Message)msg);
                ++i;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

