package cz.henrych.lunch.menu;

import java.util.Calendar;
import java.util.function.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Zatisi implements Supplier<String> {
    public static final String URL = "http://restaurantcafe.cz/denni-menu-zatisi/";

    @Override
    public String get() {
        try {
            Document doc = Jsoup.connect(URL).get();
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">CAFÉ ZÁTIŠÍ</a> ");
            
            Calendar today = Calendar.getInstance();            
            
            Element h4 = doc.select("h4").get(today.get(Calendar.DAY_OF_WEEK) - 2);
            out.append(h4.text()).append("<br/>");
            
            for (Element menu : h4.nextElementSiblings()) {
                if (menu.text().startsWith("more")) {
                    break;
                }
                
                if ("h4".equals(menu.normalName()) || "h3".equals(menu.normalName())) {
                    break;
                }
                
                out.append(menu.text()).append("<br/>");
            }            
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">CAFÉ ZÁTIŠÍ</a>");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }

}
