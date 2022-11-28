package cz.henrych.lunch.menu;

import java.util.function.Supplier;

public class JidloJihu implements Supplier<String> {
    public static final String URL = "https://jidlojihu.cz/";

    @Override
    public String get() {
        StringBuilder out =  new StringBuilder();
        out.append("<a href=\"").append(URL).append("\">JI DLO JI HU</a> ");
        out.append("<br/>");
        
        return out.toString();
    }

}
