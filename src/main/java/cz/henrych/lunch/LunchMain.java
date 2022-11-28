package cz.henrych.lunch;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import cz.henrych.lunch.menu.Pubs;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Controller
@SpringBootApplication
@EnableAutoConfiguration
public class LunchMain implements WebMvcConfigurer {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Autowired
    private DataSource dataSource;
    
    ThreadPoolExecutor getMenuExecutor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>(2*Pubs.values().length));
    
    private ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        SpringApplication.run(LunchMain.class, args);
    }

    @RequestMapping("/drop/{id:.+}")
    ResponseEntity<Void> drop(Map<String, Object> model, @PathVariable("id") String id) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            switch (id) {
                case "chat":
                case "votes":
                case "dilbert":
                case "menus":
                    break;
                default:
                    return null;
            }
            
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DROP TABLE " + id);
        } catch (Throwable t) {
            // Ignore
        }
        
        createTables();
        
        return null;
    }
    
    private void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS dilbert (" + "url TEXT NOT NULL,"
                + "img TEXT NOT NULL,"
                + "posting_date DATE NOT NULL DEFAULT CURRENT_DATE,"
                + "PRIMARY KEY (posting_date))");
            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS menus (" + "pub VARCHAR(50) NOT NULL,"
                + "menu TEXT NOT NULL," + "posting_date DATE NOT NULL DEFAULT CURRENT_DATE,"
                + "PRIMARY KEY (pub, posting_date))");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS votes (" + "clientid VARCHAR(50) NOT NULL,"
                + "vote TEXT NOT NULL," 
                + "modcount INTEGER DEFAULT 0,"
                + "posting_date DATE NOT NULL DEFAULT CURRENT_DATE,"
                + "PRIMARY KEY (clientid, posting_date, modcount))");
            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS gvotes (" + "clientid VARCHAR(50) NOT NULL,"
                + "groupid INTEGER DEFAULT 0,"
                + "posting_date DATE NOT NULL DEFAULT CURRENT_DATE,"
                + "PRIMARY KEY (clientid, posting_date, groupid))");
            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS chat (" 
                + "user_name TEXT NOT NULL,"
                + "msg TEXT NOT NULL,"
                + "msg_time VARCHAR(20) NOT NULL,"
                + "posting_date DATE NOT NULL DEFAULT CURRENT_DATE,"
                + "id SERIAL,"
                + "PRIMARY KEY (id))");
        }   
    }
    
    private void purgeTables() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();
            LocalDate day  = LocalDate.now().minusDays(32);
            stmt.executeUpdate("DELETE FROM dilbert WHERE posting_date <= TO_DATE('" + day.toString() + "', 'YYYY-MM-DD')");
            stmt.executeUpdate("DELETE FROM menus WHERE posting_date <= TO_DATE('" + day.toString() + "', 'YYYY-MM-DD')");
            stmt.executeUpdate("DELETE FROM votes WHERE posting_date <= TO_DATE('" + day.toString() + "', 'YYYY-MM-DD')");
            stmt.executeUpdate("DELETE FROM gvotes WHERE posting_date <= TO_DATE('" + day.toString() + "', 'YYYY-MM-DD')");
            stmt.executeUpdate("DELETE FROM chat WHERE posting_date <= TO_DATE('" + day.toString() + "', 'YYYY-MM-DD')");
        }   
    }
    
    @PostConstruct
    public void init() throws SQLException {
        createTables();
        purgeTables();
    }
    
    @RequestMapping("/")
    String index(Map<String, Object> model) throws SQLException {
        model.put("CLIENT_ID", UUID.randomUUID().toString());
        model.put("GROUP_ID", 0);
        fillMenus(model);
        return "index";
    }
    
    @RequestMapping("/edit")
    String edit(Map<String, Object> model,
        @RequestParam("clientid") String clientId) throws SQLException, IOException {
        model.put("CLIENT_ID", clientId);
        
        Map<String, Object> vote = getVote(clientId);      
        model.put("USER", vote.get("user"));
        model.put("GROUP_ID", getGroup(clientId));
        if ("off".equalsIgnoreCase((String)vote.getOrDefault("ucast", "on"))) {
            for (Pubs pub: Pubs.values()) {
                vote.put(pub.name(), 11);
            }
        }
        model.put("VOTE", vote);
        
        fillMenus(model);
        return "index";
    }
    
    @RequestMapping("/group")
    String changeGroup(Map<String, Object> model,
        @RequestParam("clientid") String clientId,
        @RequestParam("groupid") int groupId) throws SQLException, IOException {
        
        storeGroup(clientId, groupId == 0 ? 1 : 0);
        
        return resultsInner(model, clientId);
    }
    
    @RequestMapping(value = "/group",
        method = {RequestMethod.POST},
        consumes = MediaType.APPLICATION_JSON_VALUE)
    String changeGroup(Map<String, Object> model, @RequestBody Map<String, String> body) throws SQLException, IOException {
        
        Integer groupId = Integer.valueOf(body.getOrDefault("groupid", "0"));
        String clientId = body.get("clientid");
        storeGroup(clientId, groupId == 0 ? 1 : 0);
        
        return resultsInner(model, clientId);        
    }
    
    @RequestMapping("/delete")
    String delete(Map<String, Object> model,
        @RequestParam("clientid") String clientId) throws SQLException, IOException {
        model.put("CLIENT_ID", clientId);
        model.put("GROUP_ID", 0);

        try (Connection connection = dataSource.getConnection()) {
            String deleteQuery = "DELETE from votes where posting_date = CURRENT_DATE and clientid = ?";
            PreparedStatement preparedStmt = connection.prepareStatement(deleteQuery);
            preparedStmt.setString(1, clientId);
            preparedStmt.executeUpdate();
        }

        return resultsInner(model, "0");
    }

    private void fillMenus(Map<String, Object> model) throws SQLException {
        fillPubs(model);

        Map<String, String> menus = getMenus(false);
        model.put("MENUS", menus);
    }

    private void fillPubs(Map<String, Object> model) {
        model.put("HOSPODYL", Arrays.asList(Pubs.rightsStr));
        model.put("HOSPODYR", Arrays.asList(Pubs.leftsStr));
        
        model.put("HOSPODY", Arrays.asList(Pubs.values()));
    }
    
    private void fillDilbert(Map<String, Object> model) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();
            
            LocalDate today = LocalDate.now();
            int days = 1;
            switch(today.getDayOfWeek()) {
                case MONDAY:
                    days = 3;
                    break;
                default:
                    days = 1;
                    break;
            }
            
            List<String> urls = new ArrayList<>(days);
            List<String> imgs = new ArrayList<>(days);
            for (int i = 0; i< days; i++) {
                try {
                    LocalDate day = today.minusDays(i);
                    
                    boolean found = false;
//                    boolean forceRefresh = true;
//                    if (forceRefresh) {
//                        stmt.executeUpdate(
//                            "DELETE FROM dilbert where posting_date = CURRENT_DATE");
//                    } else 
                    {
                        ResultSet rs = stmt.executeQuery(
                            "SELECT * FROM dilbert where posting_date = TO_DATE('" + day.toString() + "', 'YYYY-MM-DD')");
                        while (rs.next()) {
                            urls.add(rs.getString("url"));
                            imgs.add(rs.getString("img"));
                            found = true;
                        }
                    }
                    
                    if (found) {
                        continue;
                    }
                    
                    String dilbertUrl = "https://dilbert.com/strip/" + day;
                    String imgUrl = "";
                    Document doc = Jsoup.connect(dilbertUrl).get();
                    for (Element div : doc.select("div.comic-item-container")) {
                        for (Element img : div.select("img.img-responsive")) {
                            imgUrl = img.attr("src");
                        }
                    }
                    
                    String insertQuery = "INSERT INTO dilbert (url, img, posting_date)  VALUES (?, ?, ?)";
                    PreparedStatement preparedStmt = connection.prepareStatement(insertQuery);
                    preparedStmt.setString(1, dilbertUrl);
                    preparedStmt.setString(2, imgUrl);
                    preparedStmt.setDate(3, new java.sql.Date(Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()).getTime()));
                    preparedStmt.execute();
                    
                    urls.add(dilbertUrl);
                    imgs.add(imgUrl);
                } catch (Throwable t) {
                    System.err.println(t);
                }
            }
            
            Collections.reverse(urls);
            Collections.reverse(imgs);
            model.put("DILBERT_URL", urls);
            model.put("DILBERT_IMG", imgs);
        }
    }
    
    @RequestMapping(value = "/results",
        method = {RequestMethod.POST},
        consumes = MediaType.APPLICATION_JSON_VALUE)
    String results(Map<String, Object> model, @RequestBody Map<String, String> body) throws SQLException, IOException {
        
        boolean attend = ("off".equalsIgnoreCase(body.getOrDefault("ucast", "on")));
        Map<String, Object> vote = new HashMap<>();
        for (Pubs pub : Pubs.values()) {
            if (attend) {
                vote.put(pub.name(), 15);
            } else if (body.containsKey(pub.name())) { 
                try {
                    String v = body.get(pub.name());
                    Integer i = Integer.valueOf(v);
                    if (i<10 || i > 14) {
                        throw new NumberFormatException("Vote out of range"); 
                    }
                    vote.put(pub.name(), i);
                } catch (NumberFormatException e) {
                    vote.put(pub.name(), 11);
                }
            } else {
                vote.put(pub.name(), 11);
            }
        }
        String clientId = body.get("clientid");
        Integer groupId = Integer.valueOf(body.getOrDefault("groupid", "0"));
        vote.put("user", body.getOrDefault("user", "N/A"));
        vote.put("ucast", body.getOrDefault("ucast", "on"));
        vote.put("clientid", clientId);
        vote.put("groupId", groupId);
        
        System.err.println("User: " + vote.get("user") + ", clientid: " + clientId + ", vote: " + vote + ", groupId: " + groupId);
        
        storeVote(vote);
        storeGroup(clientId, groupId);
        
        return resultsInner(model, clientId);        
    }
    
    @RequestMapping("/results")
    String results(Map<String, Object> model, 
        @CookieValue(value = "clientid", required=false) String clientId,
        @RequestParam(value="clientId", required=false) String overrideClientId,
        HttpServletResponse response) throws SQLException, JsonParseException, JsonMappingException, IOException {
        if (overrideClientId != null) {
            response.addCookie(new Cookie("clientid", overrideClientId));
        }
        clientId = overrideClientId == null ? clientId : overrideClientId;
        if (clientId == null || clientId.isEmpty()) {
            return index(model);
        }
        return resultsInner(model, clientId);
    }
        
    String resultsInner(Map<String, Object> model, String clientId) throws SQLException, JsonParseException, JsonMappingException, IOException {
        fillDilbert(model);
        fillMenus(model);
        
        fillResults(model);
        
        model.put("CLIENT_ID", clientId);
        model.put("GROUP_ID", getGroup(clientId));
        model.put("NOW", System.currentTimeMillis());
        
        return "results";
    }

    private void fillResults(Map<String, Object> model)
        throws SQLException, JsonParseException, JsonMappingException, IOException {
        Map<String, List<Map<String, Object>>> votes = getVotes();
       
        List<Result> resultsG1 = sumResults(votes, 0);
        List<Result> resultsG2 = sumResults(votes, 1);
        int numG1 = getNumAttendees(votes, 0);
        int numG2 = getNumAttendees(votes, 1);
        
        model.put("VOTES", votes);
        model.put("RESULTS1", resultsG1);
        model.put("RESULTS2", resultsG2);
        model.put("NUM1", numG1);
        model.put("NUM2", numG2);
    }
    
    private int getNumAttendees(Map<String, List<Map<String, Object>>> votes, int group) {
        int num = 0;
        for (List<Map<String, Object>> voteList : votes.values()) {
            Map<String, Object> vote = voteList.get(0);
            if (group != Integer.valueOf((String)vote.getOrDefault("groupid", "0"))) {
                continue;
            }
            if ("off".equalsIgnoreCase((String)vote.getOrDefault("ucast", "on"))) {
                continue;
            }
            num++;            
        }
        
        return num;
    }
    
    private List<Result> sumResults(Map<String, List<Map<String, Object>>> votes, int group) {
        Map<Pubs, Double> summary = new HashMap<>();
        for (List<Map<String, Object>> voteList : votes.values()) {
            Map<String, Object> vote = voteList.get(0);
            if (group != Integer.valueOf((String)vote.getOrDefault("groupid", "0"))) {
                continue;
            }
            for (Pubs pub : Pubs.values()) {
                if (vote.get(pub.name()) instanceof Integer) {
                    summary.put(pub, summary.getOrDefault(pub, 0d) + vote2value((Integer)vote.get(pub.name())));
                }
            }
        }
        
        List<Pair<Double, Pubs>> vysledekTmp= new ArrayList<>();
        for (Entry<Pubs, Double> e : summary.entrySet()) {
            vysledekTmp.add(new Pair<>(e.getValue(), e.getKey()));
        }
        vysledekTmp.sort(new Comparator<Pair<Double, Pubs>>() {
            @Override
            public int compare(Pair<Double, Pubs> o1, Pair<Double, Pubs> o2) {
                int ret = -1 * o1.getFirst().compareTo(o2.getFirst());
                if (ret == 0) {
                    return o1.getSecond().ordinal() - o2.getSecond().ordinal();
                } 
                return ret;
            }
        });
        
        List<Result> results = new ArrayList<>(vysledekTmp.size());
        String previous = null;
        boolean winner = true;
        boolean space = false;
        for (Pair<Double, Pubs> p : vysledekTmp) {
            String value = new DecimalFormat("#.###").format(p.getFirst());
            if (!Objects.equals(value, previous)) {
                winner = previous == null;
                space = previous != null;
            }
            previous = value;
            results.add(new Result(value, p.getSecond().name(), p.getSecond().getIcon(), winner, space));
            space = false;
        }
        
        return results;
    }
    
    @RequestMapping(value = "/ajax_chat",
        method = {RequestMethod.POST},
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    String chat(Map<String, Object> model,
        @RequestParam("user") String user,
        @RequestParam("msg") String msg,
        @RequestParam(value="time", required=false) String time,
        @RequestParam("ajax_version") String ajaxVersion) throws SQLException, IOException {
        if (user == null || msg == null || ajaxVersion == null) {
            throw new IllegalArgumentException("Nulls not allowed");
        }
        
        boolean refresh = "@@@refresh".equalsIgnoreCase(msg);
        if (!msg.isEmpty()) {
            if (refresh) {
                ajaxVersion = "REFRESH_ME";
            } else {
                // Store chat
                try (Connection connection = dataSource.getConnection()) {
                    String insertQuery = "INSERT INTO chat (user_name, msg, msg_time) VALUES (?,?,?)";
                    PreparedStatement preparedStmt = connection.prepareStatement(insertQuery);
                    preparedStmt.setString(1, user);
                    preparedStmt.setString(2, msg);
                    preparedStmt.setString(3, time);
                    preparedStmt.execute();
                }
            }
        }

        int version = 0;
        if (!"REFRESH_ME".equalsIgnoreCase(ajaxVersion)) {
            try {
                version = Integer.valueOf(ajaxVersion);
            } catch (NumberFormatException e) {
                System.err.println("Wrong ajax_version: " + ajaxVersion);
            }
        }
        
        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs;
            if (refresh) {
                String selectQuery = "SELECT * FROM chat where posting_date = CURRENT_DATE ORDER BY id desc";
                PreparedStatement preparedStmt = connection.prepareStatement(selectQuery);
                rs = preparedStmt.executeQuery();
            } else {
                String selectQuery = "SELECT * FROM chat where posting_date = CURRENT_DATE AND id > ? ORDER BY id desc";
                PreparedStatement preparedStmt = connection.prepareStatement(selectQuery);
                preparedStmt.setInt(1, version);
                rs = preparedStmt.executeQuery();
            }
            
            List<ChatLine> chats = new ArrayList<>();
            int maxId = version;
            while (rs.next()) {
                int id = rs.getInt("id");
                if (maxId < id) {
                    maxId = id;
                }
                chats.add(new ChatLine(rs.getString("user_name"), rs.getString("msg"), rs.getString("msg_time")));
            }
            
            model.put("CHAT", chats);
            model.put("AJAX_VERSION", maxId);
            
            //System.out.println(new Date().toString() + " " + ajaxVersion + "->" + maxId + " AJAX chat: " + chats);
        }     
        
        //return new ResponseEntity<>(mapper.writeValueAsString(model), HttpStatus.OK);
        return "fragments::ajax_chat_text";
    }
    
    @RequestMapping("/ajax_results")
    public String ajaxResults(Map<String, Object> model, @CookieValue("clientid") String clientId) throws SQLException, JsonParseException, JsonMappingException, IOException {
        fillPubs(model);
        fillResults(model);
        model.put("CLIENT_ID", clientId);
        model.put("GROUP_ID", getGroup(clientId));
        model.put("NOW", System.currentTimeMillis());
        return "fragments::ajax_results";
    }
    
    private double vote2value(int vote) {
        switch (vote) {
            case 10:
                return -0.5;
            case 11:
                return 0;
            case 12:
                return 0.01;
            case 13:
                return 0.9;
            case 14:      
                return 1;
            default:
                return 0;
        }
    }

    private Map<String, String> getMenus(boolean forceRefresh) throws SQLException {
        Set<Pubs> notReadyPubs = Collections.newSetFromMap(new ConcurrentHashMap<>());
        final Map<String, String> menus = new HashMap<>();
        for (Pubs pub : Pubs.values()) {
            String menu = getMenu(pub, forceRefresh, false);
            if (menu == null) {
                notReadyPubs.add(pub);
            } else  {
                menus.put(pub.name(), menu);
            }
        }
        if (!notReadyPubs.isEmpty()) {
            for (Pubs pub : notReadyPubs.toArray(new Pubs[0])) {
                getMenuExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String menu = getMenu(pub, forceRefresh, true);
                            menus.put(pub.name(), menu);
                            notReadyPubs.remove(pub);
                        } catch (SQLException e) {
                            System.err.println(e);
                        }
                    }
                });
            }
            
            while(!notReadyPubs.isEmpty()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println(e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        return menus;
    }
    
    @SuppressWarnings("unchecked")
    private void storeVote(Map<String, Object> vote) throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection()) {
            String selectQuery = "SELECT max(modcount) from votes where posting_date = CURRENT_DATE and clientid = ?";
            PreparedStatement preparedStmt = connection.prepareStatement(selectQuery);
            preparedStmt.setString(1, (String)vote.get("clientid"));
            ResultSet rs = preparedStmt.executeQuery();
            int max = -1;
            if (rs.next()) {
                max = rs.getInt(1);
            }            
            
            Map<String, Object> oldVote = Collections.emptyMap();
            if (max >= 0) {
                selectQuery = "SELECT vote from votes where posting_date = CURRENT_DATE and clientid = ? and modcount = ?";
                preparedStmt = connection.prepareStatement(selectQuery);
                preparedStmt.setString(1, (String)vote.get("clientid"));
                preparedStmt.setInt(2, max);
                rs = preparedStmt.executeQuery();
                if (rs.next()) {
                    oldVote = new HashMap<String, Object>(mapper.readValue(rs.getString("vote"), Map.class));
                }
            }
            
//            Set<Object> a = new HashSet<>(oldVote.values());
//            Set<Object> b = new HashSet<>(oldVote.values());
//            Set<Object> c = new HashSet<>(vote.values());
//            a.removeAll(c);
//            c.removeAll(b);
//            
//            if (a.isEmpty() && c.isEmpty()) {
//                System.out.println("Same vote. Ignoring");
//                return;
//            }
            
            String insertQuery = "INSERT INTO votes (clientid, vote, modcount) VALUES (?,?,?)";
            preparedStmt = connection.prepareStatement(insertQuery);
            preparedStmt.setString(1, (String)vote.get("clientid"));
            preparedStmt.setString(2, mapper.writeValueAsString(vote));
            preparedStmt.setInt(3, max + 1);
            preparedStmt.execute();
        }
    }
    
    private void storeGroup(String clientId, int group) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String deleteQuery = "DELETE from gvotes where posting_date = CURRENT_DATE and clientid = ?";
            PreparedStatement preparedStmt = connection.prepareStatement(deleteQuery);
            preparedStmt.setString(1, clientId);
            preparedStmt.executeUpdate();
            
            String insertQuery = "INSERT INTO gvotes (clientid, groupid)  VALUES (?,?)";
            preparedStmt = connection.prepareStatement(insertQuery);
            preparedStmt.setString(1, clientId);
            preparedStmt.setInt(2, group);
            preparedStmt.execute();
        }
        
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getVote(String clientId) throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection()) {
            String deleteQuery = "SELECT * from votes where posting_date = CURRENT_DATE and clientid = ? "
                + " and modcount = (select max(modcount) from votes where posting_date = CURRENT_DATE and clientid = ?)";
            PreparedStatement preparedStmt = connection.prepareStatement(deleteQuery);
            preparedStmt.setString(1, clientId);
            preparedStmt.setString(2, clientId);
            ResultSet rs = preparedStmt.executeQuery();
            
            while (rs.next()) {
                return mapper.readValue(rs.getString("vote"), Map.class);
            }
            return Collections.emptyMap();
        }
    }
    
    private int getGroup(String clientId) throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection()) {
            String deleteQuery = "SELECT * from gvotes where posting_date = CURRENT_DATE and clientid = ?";
            PreparedStatement preparedStmt = connection.prepareStatement(deleteQuery);
            preparedStmt.setString(1, clientId);
            ResultSet rs = preparedStmt.executeQuery();
            
            while (rs.next()) {
                return rs.getInt("groupid");
            }
            return 0;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> getVotes() throws SQLException, JsonParseException, JsonMappingException, IOException {
        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT g.groupid, v.* FROM votes v JOIN gvotes g ON g.clientid=v.clientid AND g.posting_date=v.posting_date "
                + " where v.posting_date = CURRENT_DATE ORDER BY modcount DESC");
            
            Map<String, Map<String, Object>> retvalTmp = new TreeMap<>();
            while (rs.next()) {
                Map<String, Object> vote = mapper.readValue(rs.getString("vote"), Map.class);
                String clientId = rs.getString("clientid");
                vote.put("groupid", rs.getString("groupid"));
                retvalTmp.put(rs.getString("groupid") + "||||"  + (String)vote.getOrDefault("user", "") + "||||" + String.format("%05d", 99999 - rs.getInt("modcount")) + "|" + clientId, vote);
            }
            
            String prevGroupId = null;
            Map<String, List<Map<String, Object>>> retval = new LinkedHashMap<>();
            for (Map<String, Object> v : retvalTmp.values()) {
                String clientId = (String)v.get("clientid");
                String groupId = (String)v.get("groupid");
                boolean changed = !Objects.equals(prevGroupId, groupId);
                v.put("groupstart", changed);
                prevGroupId = groupId;
                List<Map<String, Object>> votes = retval.get(clientId);
                if (votes == null) {
                    votes = new ArrayList<>();
                    retval.put(clientId, votes);
                }
                votes.add(v);
                retval.put(clientId, votes);
            }
            
            return retval;
        }
    }


    @RequestMapping("/menus")
    String db(Map<String, Object> model) {
        try {
            ArrayList<String> output = new ArrayList<String>();

            Map<String, String> menus = getMenus(false);
            for (String menu : menus.values()) {
                output.add(menu);
            }

            model.put("records", output);
            return "db";
        } catch (Exception e) {
            model.put("message", e.getMessage());
            return "error";
        }
    }

    @RequestMapping("/refresh")
    String refresh(Map<String, Object> model) {
        try {
            ArrayList<String> output = new ArrayList<String>();

            Map<String, String> menus = getMenus(true);
            for (String menu : menus.values()) {
                output.add(menu);
            }

            model.put("records", output);
            return "db";
        } catch (Exception e) {
            model.put("message", e.getMessage());
            return "error";
        }
    }
        
    @RequestMapping("/refresh/{id:.+}")    
    String refresh(Map<String, Object> model, @PathVariable("id") String id) {
        try {
            ArrayList<String> output = new ArrayList<String>();

            Pubs pub = Pubs.valueOf(id);
            Map<String, String> menus = Collections.singletonMap(pub.name(), getMenu(pub, true, true));
            for (String menu : menus.values()) {
                output.add(menu);
            }

            model.put("records", output);
            return "db";
        } catch (Exception e) {
            model.put("message", e.getMessage());
            return "error";
        }
    }
    
    
    @RequestMapping(value = "/replace/{id:.+}",
        method = {RequestMethod.POST},
        consumes = MediaType.TEXT_PLAIN_VALUE)    
    String replaceMenu(Map<String, Object> model, 
        @PathVariable("id") String id,
        @RequestHeader("lunch") String lunchSecret,
        @RequestBody String body) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(lunchSecret.getBytes());
            if ( !Arrays.equals(digest, new byte[] { -42, 113, -54, -24, 93, -58, -42, -57, 50, 19, -91, -127, 52, -125, 33, -46} ) ) {
                // TODO: 401
                throw new IllegalArgumentException("Authorization header is invalid");
            }
            
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();
                stmt.executeUpdate(
                    "DELETE FROM menus where posting_date = CURRENT_DATE and pub = '" + id + "'");
                
                try {
                    String insertQuery = "INSERT INTO menus (pub, menu) VALUES (?,?)";
                    PreparedStatement preparedStmt = connection.prepareStatement(insertQuery);
                    preparedStmt.setString(1, id);
                    preparedStmt.setString(2, body);
                    preparedStmt.execute();
                } catch (Throwable t) {
                    System.err.println("Failed to insert menu: " + t);
                }
            }
            
            model.put("records", Collections.singletonList(body));
            return "db";
        } catch (Exception e) {
            model.put("message", e.getMessage());
            return "error";
        }
    }

    
    

    private String getMenu(Pubs pub, boolean forceRefresh, boolean query) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();
            if (forceRefresh) {
                stmt.executeUpdate(
                    "DELETE FROM menus where posting_date = CURRENT_DATE and pub = '" + pub + "'");
            } else {
                ResultSet rs = stmt.executeQuery(
                    "SELECT * FROM menus where posting_date = CURRENT_DATE and pub = '" + pub
                        + "'");
                while (rs.next()) {
                    return rs.getString("menu");
                }
            }
            if (! query) {
                return null;
            }
            
            String menu = pub.getMenu();

            try {
                String insertQuery = "INSERT INTO menus (pub, menu) VALUES (?,?)";
                PreparedStatement preparedStmt = connection.prepareStatement(insertQuery);
                preparedStmt.setString(1, pub.name());
                preparedStmt.setString(2, menu);
                preparedStmt.execute();
            } catch (Throwable t) {
                System.err.println("Failed to insert menu: " + t);
            }

            return menu;
        }
    }

    @Bean
    public DataSource dataSource() throws SQLException {
        if (dbUrl == null || dbUrl.isEmpty()) {
            return new HikariDataSource();
        } else {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            return new HikariDataSource(config);
        }
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/scripts/**")
            .addResourceLocations("classpath:/static/scripts/");
    }

}
