package cz.henrych.lunch.menu;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Stopka implements Supplier<String> {
    public static final String URL = "https://stopkarestaurace.cz/obedove-menu";
    public static final String URL2 = "https://www.facebook.com/STOPKArestaurace";

    public static class SSLHelper {

        static public Connection getConnection(String url){
            return Jsoup.connect(url).sslSocketFactory(SSLHelper.socketFactory());
        }

        static private SSLSocketFactory socketFactory() {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};

            try {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                SSLSocketFactory result = sslContext.getSocketFactory();

                return result;
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed to create a SSL socket factory", e);
            }
        }
    }
    
    @Override
    public String get() {
        try {
            Document doc = SSLHelper.getConnection(URL).get();
            
            StringBuilder out =  new StringBuilder();
//            out.append("<!-- ");
//            out.append(doc.outerHtml());
//            out.append(" -->");
            out.append("<a href=\"").append(URL2).append("\">Stopka</a> ");
            
            
            Calendar today = Calendar.getInstance();
            
            int weekday = today.get(Calendar.DAY_OF_WEEK) - 2;
            
            for (Element h2 : doc.select("h2.lunch-day-item__title")) {
                out.append(h2.text()).append("<br/>");
            }
            
            for (Element t : doc.select("table.lunch-meal-items__table")) {
                for (Element s : t.select("strong.lunch-meal-item__name")) {
                    out.append(s.text()).append("<br/>");
                }
            }
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL2).append("\">Stopka</a> ");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }

    public static void main(String[] args) {
        Stopka s = new Stopka();
        
        System.out.println(s.get());
    }
}
