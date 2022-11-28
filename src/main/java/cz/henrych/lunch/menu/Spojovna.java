package cz.henrych.lunch.menu;

import java.util.Calendar;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Spojovna implements Supplier<String> {
    public static final String URL = "http://pivovarspojovna.cz/menu/";

    @Override
    public String get() {
        try {
            Document doc = Jsoup.connect(URL).get();
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Pivovar Spojovna</a> ");
            
            Calendar today = Calendar.getInstance();
            
            Elements h4 = doc.select("h4");
            Pattern pattern = Pattern.compile(".*(" + today.get(Calendar.DAY_OF_MONTH) + ".*" + (today.get(Calendar.MONTH)+1) + ".*" + today.get(Calendar.YEAR) + ").*");
            for (Element h : h4) {
                String text = h.text();
                
                boolean afterDenniMenu = false;
                Matcher m = pattern.matcher(text);
                if (m.matches()) {
                    out.append(m.group(1)).append("<br/>");
                    Element menu = h.nextElementSibling();
                    for (Element tr : menu.select("tr")) {
                        String rowText = tr.select("td").get(1).text();
                        if (rowText.contains("OD PONDĚLÍ DO PÁTKU")) {
                            break;
                        }
                        if (rowText.contains("Denní menu ")) {
                            afterDenniMenu = true;
                            continue;
                        }
                        
                        if (afterDenniMenu &&  "".equals(rowText.trim())) {
                            continue;
                        }
                        if (rowText.contains("HOTOVÁ JÍDLA:")
                            || rowText.contains("Dezerty:")
                            || rowText.contains("Dorty:")) {
                            out.append("<small><i>").append(rowText).append("</i></small><br/>");
                        } else {
                            out.append(rowText).append("<br/>");
                        }
                    }
                }
            }
            out.append("<br/>");
            
            return out.toString();
        } catch (Exception e) {
            StringBuilder out =  new StringBuilder();
            out.append("<a href=\"").append(URL).append("\">Pivovar Spojovna</a>");
            out.append("<br/>");
            out.append(e.toString());
            return out.toString();
        }
    }

}
