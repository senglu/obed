package cz.henrych.lunch.menu;

import java.util.function.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class NaRychte implements Supplier<String> {

    private static final String URL = "http://narychte.eu/#daily";

    @Override
    public String get() {
        try {
            Document doc = Jsoup.connect(URL).get();
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Na Rychtě</a><BR/>");
            
            Element daily = doc.select("#daily div").get(0);
            for (Element food : daily.select("h6.food-title")) {
                String text = food.text();
                if (text.contains("Rozvoz obědů")) {
                    break;
                }
                if (text.contains("Polévky:") ||
                    text.contains("Doporučujeme:") ||
                    text.contains("SALÁTY:") ||
                    text.contains("Hotová jídla:") ||
                    text.contains("DEZERT") ||
                    text.contains("Dezert") ||
                    text.contains("Saláty:") ||
                    text.contains("Polední menu:")) {
                    out.append("<small><i>").append(text).append("</i></small><br/>");
                } else {
                    out.append(text).append("<br/>");
                }
            }
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Na Rychtě</a>");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }

}
