package cz.henrych.lunch;

public class Result {
    public String value;
    public String pub;
    public String icon;
    public boolean bold;
    public boolean space;
    
    public Result(String value, String pub, String icon, boolean bold, boolean space) {
        this.value = value;
        this.pub = pub;
        this.icon = icon;
        this.bold = bold;
        this.space = space;
    }

    @Override
    public String toString() {
        return "Result [value=" + value + ", pub=" + pub + ", icon=" + icon + ", bold=" + bold
            + ", space=" + space + "]";
    }
}
