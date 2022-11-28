package cz.henrych.lunch;

public class ChatLine {
    public String user;
    public String msg;
    public String time;
    
    public ChatLine(String user, String msg, String time) {
        super();
        this.user = user;
        this.msg = msg;
        this.time = time;
    }

    @Override
    public String toString() {
        return "ChatLine [user=" + user + ", msg=" + msg + ", time=" + time + "]";
    }
    
}
