package cz.henrych.lunch.menu;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Presto implements Supplier<String> {
    public static final String URL = "http://www.prestorestaurant.cz/cz/click/chodov/1/";

    @Override
    public String get() {
        try {
            Document doc = Jsoup.connect(URL).get();
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Presto</a><br/>");
            
            
            Set<String> ignored = new HashSet<>();
            ignored.addAll(Arrays.asList(new String[] {
                    "Saláty", "Zelenina", "Přílohy", "Omáčky"
            }));
            for (Element sec : doc.select("section")) {
                boolean ignore = false;
                String text = null;
                for (Element strong : sec.select("strong")) {
                    if (text == null) {
                        text = strong.text();
                    }
                    ignore = ignore || ignored.contains(strong.text());
                }
                if (ignore) {
                    continue;
                }
                
                out.append("<i><small>").append("" + text).append(":</small></i><br/>\n");
                for (Element hgroup : sec.select("hgroup")) {
                    out.append(hgroup.text()).append("<br/>\n");
                }
            }
            
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Presot</a>");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }
    
    public static void main(String[] args) {
        Presto p = new Presto();
        System.out.println(p.get());
    }
}
