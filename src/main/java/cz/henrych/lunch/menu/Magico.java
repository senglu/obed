package cz.henrych.lunch.menu;

import java.util.Calendar;
import java.util.function.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Magico implements Supplier<String> {
    public static final String URL = "http://elmagico.cz/restaurace/denni-menu/";

    @Override
    public String get() {
        try {
            Document doc = Jsoup.connect(URL).get();
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Magico</a> <i>");
            
            Calendar today = Calendar.getInstance();
            
            int weekday = today.get(Calendar.DAY_OF_WEEK) - 2;
            Element day = doc.select("div.lunch_box").get(weekday);
            try {
                out.append(day.select("img").get(0).attr("alt")).append(" ");
                out.append(day.select("p").get(0).text()).append("</i><br/>");
            } catch (Throwable t) {
                out.append("????</i><br/>");
            }
            
            for (Element td : day.select("td.td_name")) {
                out.append(td.text()).append("<br/>");
            }
            
            if (day.select("td").isEmpty()) {
                for (Element h3 : day.select("h3")) {
                    out.append(h3.text()).append("<br/>");
                }
            }
            
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Magico</a>");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }
}
