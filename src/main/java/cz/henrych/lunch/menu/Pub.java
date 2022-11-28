package cz.henrych.lunch.menu;

import java.util.Calendar;
import java.util.function.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Pub implements Supplier<String> {
    public static final String URL = "https://www.thepub.cz/praha-11/poledni-menu/";

    @Override
    public String get() {
        try {
            Document doc = Jsoup.connect(URL).get();
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">The PUB</a> <br/>");
            
            Calendar today = Calendar.getInstance();
            
            int weekday = today.get(Calendar.DAY_OF_WEEK) - 2;
            
            boolean inDay = false;
            String[] dny = new String[] { "Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek" };
            for (Element tr : doc.select("table.menu").get(0).select("tr")) {
                Elements h2 = tr.select("h2");
                if (!h2.isEmpty()) {
                    inDay = tr.text().contains(dny[weekday]);
                } else {
                    Elements h3 = tr.select("h3");
                    if (h3.isEmpty()) {
                        if (inDay) {
                            out.append(tr.select("strong").get(0).ownText()).append("<br/>");
                        }
                    }
                }
            }
            
            out.append("<br/>");
            
            for (Element tr : doc.select("table.menu").get(1).select("tr")) {
                if (! tr.select("h2").isEmpty()) {
                    out.append("");
                } else if (tr.select("h3").isEmpty()) {
                    out.append(tr.select("strong").get(0).ownText()).append("<br/>");
                }
            }
            
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">The PUB</a>");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }
}
