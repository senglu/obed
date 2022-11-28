package cz.henrych.lunch.menu;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Jankovna implements Supplier<String> {
    public static final String URL = "http://jankovna.cz/denni-nabidka/";
    private Tesseract tesseract;
    
    Jankovna() {
        this.tesseract = new Tesseract();
        tesseract.setLanguage("ces");
    }
    
    @Override
    public String get() {
        try {
			if (true) {
				throw new Exception("");
			}
            Document doc = Jsoup.connect(URL).get();
            
            List<String> urls = new ArrayList<>();
            for (Element fig : doc.select(".wp-block-image") ) {
                for (Element img : fig.select("img")) {
                    String srcset = img.attr("srcset") ;
                    for (String s : srcset.split(", ")) {
                        String[] urlW = s.split(" ", 2);
                        if (urlW[0].contains("x")) {
                            continue;
                        }
                        urls.add(urlW[0]);
                        break;
                    }
                }
            }
            
            System.out.println("URLs: " + urls);
            String ret = tryOCR(urls.toArray(new String[urls.size()]));
            if (ret != null) {
                return "<a href=\"" + URL+ "\">Jankovna</a><br/><PRE>" + ret + "</PRE>";
            }
            
        } catch (Throwable e) {
            System.err.println("Exception: " + e);
            e.printStackTrace();
            return "<a href=\"" + URL+ "\">Jankovna</a><br/>&nbsp;<br/>";
        }
        
        return "<a href=\"" + URL+ "\">Jankovna</a><br/>&nbsp;<br/>";
    }
    
    private String tryOCR(String... urls) {
        Calendar today = Calendar.getInstance();
        int weekday = today.get(Calendar.DAY_OF_WEEK) - 2;
        if (weekday < 0 || weekday >= 5) {
            return null;
        }
        String[] start = new String[] {"PONDĚLÍ", "ÚTERÝ", "STŘEDA", "ČTVRTEK", "PÁTEK"};
        String[] end = new String[] {"ÚTERÝ", "STŘEDA", "Speciální nabídka", "PÁTEK", "SOBOTA"};
        
        Pattern p = Pattern.compile("(" + start[weekday] + ".*)" + end[weekday], Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNIX_LINES | Pattern.UNICODE_CHARACTER_CLASS);
        
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpg");
            if (!readers.hasNext()) {
                return "Unable to do OCR - no image reader";
            }
            
            ImageReader reader = readers.next();            
            for (String url : urls) {
                System.out.println("URL: " + url);
                //try (ImageInputStream iis = ImageIO.createImageInputStream(new File("/Users/vaclavhenrych/personal/dev/heroku/kam-dnes-na-obed/jankovna.jpg"));) {
                try (ImageInputStream iis = ImageIO.createImageInputStream(new java.net.URL(url).openStream())) {
                    reader.setInput(iis);
                    int imageTotal = reader.getNumImages(true);
                    System.out.println("image total: " + imageTotal);
                    for (int i = 0; i < imageTotal; i++) {
                        IIOImage oimage = reader.readAll(i, reader.getDefaultReadParam());
                        System.out.println("Image read");
                        int x = oimage.getRenderedImage().getMinX();
                        int y = oimage.getRenderedImage().getMinY();
                        int w = oimage.getRenderedImage().getWidth();
                        int h = oimage.getRenderedImage().getHeight();
                        System.out.println("Image " + x + "," + y + " : " + w + "," + h);
                        int lastGood = x+50;
                        int lastBad = x+w/4;
                        if (w == 1242) {
                            lastGood = 142;
                            lastBad = 142;
                        }
                        String lastGoodText = null;
                        int x2 = lastGood;
                        do {
                            System.out.println("OCR (before): " + x2 + "," + y + " : " +  (w-(x2-x)) + "," + h);
                            String t = tesseract.doOCR(Collections.singletonList(oimage),
                                new Rectangle(x2, y, w-(x2-x), h));
                            System.out.println("OCR (after): " + t);
                            boolean contains = t.replaceAll("[^0-9:]", "").contains("11:0015:0011:0015:00");
                            System.out.println(x2 +" - " + contains);
                            if (contains) {
                                lastGood = x2; 
                                lastGoodText = t;
                            } else {
                                lastBad = x2;
                            }
                            x2 = lastGood + (lastBad-lastGood) / 2;                            
                        }  while (lastBad - lastGood > 5);
                        
                        System.out.println("" + lastGoodText);
                        if (lastGoodText != null) {
                            Matcher m = p.matcher(lastGoodText);
                            if (m.find()) {
                                return m.group(1);
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("IOException: " + e);
                    e.printStackTrace();
                } finally {
                    reader.setInput(null);
                }
            }
        } catch (TesseractException e) {
            System.err.println("TesseractException: " + e);
            e.printStackTrace();
        }
        
        System.out.println("OCR returns null");
        return null;
                           
    }

    public static void main(String[] args) {
        Jankovna j = new Jankovna();
        System.out.println("\n\nFINAL:\n" + j.get());
    }

}
