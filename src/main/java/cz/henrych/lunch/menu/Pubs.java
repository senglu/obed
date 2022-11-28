package cz.henrych.lunch.menu;

import java.util.function.Supplier;

public enum Pubs {
    NaRychte(new NaRychte(), "na-rychte2.svg"),
    //Magico(new Magico(), "burger.gif"),
    //NaSadce(new NaSadce(), "fish.png"),
    Spojovna(new Spojovna(), "spojovna.png"),
    Pub(new Pub(), "pub.png"),
    Pepe(new Pepe(), "pepe.png"),
    Zatisi(new Zatisi(), "zatisi.png"),
    //Jankovna(new Jankovna(), "jankovna.png"),
    //Presto(new Presto(), "presto.png"),
    Stopka(new Stopka(), "stop.svg"),
    JidloJihu(new JidloJihu(), "dragon.png")
    ;
    
    private Supplier<String> getMenu;
    private String icon;
    
    public static String[] rightsStr;
    public static String[] leftsStr;
    
    static {
        Pubs[] r = new Pubs[] { NaRychte, Spojovna, Zatisi, };
        Pubs[] l = new Pubs[] { Pub, /*Presto,*/ Pepe,  Stopka, JidloJihu };
        rightsStr = new String[r.length];
        leftsStr = new String[l.length];
        for (int i=0; i<r.length; i++) {
            rightsStr[i] = r[i].name();
        }
        for (int i=0; i<l.length; i++) {
            leftsStr[i] = l[i].name();
        }
    }
    
    Pubs(Supplier<String> getMenu, String icon) {
        this.getMenu = getMenu;
        this.icon = icon;
    }
    
    public String getMenu() {
        return getMenu.get();
    }
    
    public String getIcon() {
        return icon;
    }
}
