package cz.henrych.lunch.menu;

import java.util.Calendar;
import java.util.function.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class NaSadce implements Supplier<String> {
    public static final String URL = "http://nasadce.com/#poledni";

    @Override
    public String get() {
        try {
            Document doc = Jsoup.connect(URL).get();
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Na Sádce</a> ");
            
            Calendar today = Calendar.getInstance();
            
            int weekday = today.get(Calendar.DAY_OF_WEEK) - 2;
            
            for (Element h3 : doc.select("div.poledni-menu")) {
                out.append("<i>").append(h3.select("li.et_pb_tab_" + weekday).get(0).text()).append("</i></br>");
                
                for (Element den : h3.select("div.et_pb_tab_" + weekday)) {
                    for (Element tr : den.select("tr")) {
                        out.append(tr.select("td").get(0).text()).append("<br/>");
                    }
                }
            }
            
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Na Sádce</a> ");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }

}
