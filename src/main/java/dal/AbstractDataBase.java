package dal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.Date;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AbstractDataBase {

    private final static Logger _LOG = LoggerFactory.getLogger(AbstractDataBase.class);

    /**
     * Connection settings: URL, User, Password!
     */
    protected boolean _AUTOCOMMIT = true;
    private final String _url, _user, _pwd;

    private final Map<Thread, Connection> _connections = new HashMap<>();

    AbstractDataBase(String url, String name, String password) {
        if(!url.startsWith("jdbc:sqlite:")) {
            String path = new File("").getAbsolutePath().replace("\\","/");
            url = "jdbc:sqlite:"+path+"/"+url;
        }
        if ( !url.endsWith(".db") ) {
            url += "/sqlite.db";
        }
        _user = name;
        _pwd = password;
        _url = url;
        // We make sure that the path exists, if not we create it
        File file = new File(url.replace("jdbc:sqlite:",""));
        if( !file.exists() ) {
            file.mkdirs();
        }
        try {
            _createAndOrConnectToDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getURL(){ return _url; }

    /**
     * Connect to a simple database
     */
    protected void _createAndOrConnectToDatabase() throws SQLException
    {
        _LOG.info("Establishing connection to database url '"+_url+"' now!");
        try {
            Class<?> dbDriver = Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            _LOG.error("Failed to load 'org.sqlite.JDBC' class!");
            throw new SQLException("Missing SQLite driver! Failed to load 'org.sqlite.JDBC' class!");
        }
        Connection connection = null;
        _LOG.info("Connecting to database at '{}' now!", _url);
        if (_user.equals("") || _pwd.equals(""))
            connection = DriverManager.getConnection(_url);
        else
            connection = DriverManager.getConnection(_url, _user, _pwd);
        connection.setAutoCommit(_AUTOCOMMIT);
        _connections.put(Thread.currentThread(), connection);
    }

    /**
     * This method commits on the current connection and
     * also stores error information in the response if said
     * attempt fails...
     */
    protected void _commit() {
        try {
            if(_connections.containsKey(Thread.currentThread()) && _connections.get(Thread.currentThread()) != null){
                if(!_AUTOCOMMIT)_connections.get(Thread.currentThread()).commit();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closing Connection!
     */
    protected void _close(){
        try {
            if(_connections.containsKey(Thread.currentThread()) && _connections.get(Thread.currentThread()) != null){
                _connections.get(Thread.currentThread()).close();
                _connections.put(Thread.currentThread(), null);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //_connection=null;
    }

    public void close(){
        _close();
    }

    /**
     * Returns a list of all table names of a connection!
     */
    public List<String> listOfAllTableNames(){
        String sql = "SELECT name FROM sqlite_master WHERE type ='table' AND name NOT LIKE 'sqlite_%';";
        List<String> names = new ArrayList<>();
        _for(sql, null, rs -> {
            try {
                names.add(rs.getString("name"));
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        });
        return names;
    }

    protected Map<String, List<String>> _tablesSpace(){
        String sql = "SELECT * FROM sqlite_master WHERE type ='table' AND name NOT LIKE 'sqlite_%';";
        Map<String, List<String>> space = new HashMap<>();
        _for(sql, null, rs -> {
            try {
                String sqlCode = rs.getString("sql")
                        .replace("  ", " ")
                        .replace("  ", " ")// Format for parsing!
                        .trim();
                List<String> foundAttributes = new ArrayList<>();
                List<String> foundForeigns = new ArrayList<>();
                StringBuilder builder = new StringBuilder();
                int depth = 0;
                for(int i=0; i<sqlCode.length(); i++)
                {
                    char c = sqlCode.charAt(i);
                    if ( c==')' ) depth--;
                    if ( c==',' || (depth==0 && c==')') ) {
                        String asStr = builder.toString().replace("(id)", "").trim();
                        if(asStr.toUpperCase().startsWith("FOREIGN KEY")) foundForeigns.add(asStr);
                        else foundAttributes.add(asStr);
                        builder = new StringBuilder();
                    } else if ( depth >= 1 ) builder.append(c);
                    if ( c == '(' ) depth++;
                }
                for (String foreign : foundForeigns) {
                    String variable = foreign.split("\\(")[1].split("\\)")[0].trim();
                    for(int i=0; i<foundAttributes.size(); i++) {
                        String attribute = foundAttributes.get(i);
                        if(attribute.contains(variable)) {
                            String newTail = foreign.replace("FOREIGN KEY ("+variable+")", "").trim();
                            foundAttributes.set(i, attribute+" "+newTail);
                        }
                    }
                }
                foundAttributes.sort(Comparator.comparingInt(s -> s.split(" ")[0].length()));
                space.put(rs.getString("name"), foundAttributes);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        });
        return space;
    }

    protected Map<String, List<String>> __attributesPropertiesTableOf(List<String> attributeList)
    {
        Map<String, List<String>> attributes = new HashMap<>();
        for( String a : attributeList ){
            String[] split = a.split(" ");
            String key = split[0];
            String[] value = new String[split.length-1];
            System.arraycopy(split, 1, value, 0, value.length);
            attributes.put(key, Arrays.asList(value));
        }
        return attributes;
    }


    protected int _lastInsertID() {
        return (Integer)_query("SELECT last_insert_rowid()").get("last_insert_rowid()").get(0);
    }

    private PreparedStatement _newPreparedStatement(String sql, List<Object> values) throws SQLException {
        PreparedStatement pstmt = _connections.get(Thread.currentThread()).prepareStatement(sql);
        if(values!=null) {
            for(int i=0; i<values.size(); i++) pstmt.setObject(i+1, values.get(i));
        }
        return pstmt;
    }

    protected void _for(String sql, Consumer<ResultSet> start, Consumer<ResultSet> each)
    {
        _for(sql, null, start, each);
    }

    protected void _for(
            String sql,
            List<Object> values,
            Consumer<ResultSet> start,
            Consumer<ResultSet> each
    ){
        if (values!=null && !values.isEmpty()){
            try {
                PreparedStatement pstmt = _newPreparedStatement(sql, values);
                try {
                    ResultSet rs = pstmt.executeQuery();// loop through the result set
                    if ( start != null && !rs.isClosed() )
                        start.accept(rs);
                    if ( each != null && !rs.isClosed() ) {
                        if ( start == null )
                            while (rs.next()) each.accept(rs);
                        else
                            do each.accept(rs); while (rs.next());
                    }
                    rs.close();
                    pstmt.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Statement stmt = _connections.get(Thread.currentThread()).createStatement();
                try {
                    ResultSet rs = stmt.executeQuery(sql);// loop through the result set
                    if ( start != null && !rs.isClosed() )
                        start.accept(rs);
                    if ( each != null && !rs.isClosed() ) {
                        if ( start == null )
                            while (rs.next()) each.accept(rs);
                        else
                            do each.accept(rs); while (rs.next());
                    }
                    rs.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


    }

    protected Map<String, List<Object>> _query(String sql) {
        return _query(sql, null);
    }

    protected Map<String, List<Object>> _query(String sql, List<Object> values){

        Map<String, List<Object>> result = new HashMap<>();
        _for(
                sql, values, // <=- Are used to build prepared statement when 'values' is not null!
                rs -> {
                    try {
                        ResultSetMetaData rsmd = rs.getMetaData();
                        int columnsNumber = rsmd.getColumnCount();
                        for (int i = 1; i <= columnsNumber; i++) {
                            result.put(rsmd.getColumnName(i), new ArrayList<>());
                        }
                    } catch (Exception e){e.printStackTrace();}
                },
                rs -> {
                    try {// loop through the result set
                        while (rs.next()) {
                            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                                String columnValue = rs.getString(i);
                                ResultSetMetaData rsmd = rs.getMetaData();
                                String column_name = rsmd.getColumnName(i);
                                if(rsmd.getColumnType(i)==java.sql.Types.ARRAY) {
                                    result.get(column_name).add(rs.getArray(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.BIGINT) {
                                    result.get(column_name).add(rs.getInt(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.BOOLEAN) {
                                    result.get(column_name).add(rs.getBoolean(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.BLOB) {
                                    result.get(column_name).add(rs.getBlob(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.DOUBLE) {
                                    result.get(column_name).add(rs.getDouble(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.FLOAT) {
                                    result.get(column_name).add(rs.getFloat(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.INTEGER) {
                                    result.get(column_name).add(rs.getInt(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.NVARCHAR) {
                                    result.get(column_name).add(rs.getNString(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.VARCHAR) {
                                    result.get(column_name).add(rs.getString(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.TINYINT) {
                                    result.get(column_name).add(rs.getInt(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.SMALLINT) {
                                    result.get(column_name).add(rs.getInt(column_name));
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.DATE) {
                                    String date = rs.getString(column_name);
                                    result.get(column_name).add((date==null)?null:Date.valueOf(date));
                                    //result.get(column_name).add(rs.getDate(column_name));
                                    //rs.getTimestamp(column_name);
                                }
                                else if(rsmd.getColumnType(i)==java.sql.Types.TIMESTAMP){
                                    result.get(column_name).add(rs.getTimestamp(column_name));
                                } else {
                                    result.get(column_name).add(rs.getObject(column_name));
                                }
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                });
        return result;
    }

    /**
     * SQL execution on connection!
     * @param sql
     */
    protected void _execute(String sql) {
        if(sql.isBlank()) return;
        Connection conn = _connections.get(Thread.currentThread());
        try {
            Statement stmt = conn.createStatement();
            try {
                stmt.execute(sql);
                stmt.close();
            } catch (SQLException e) {
                stmt.close();
                e.printStackTrace();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * SQL execution on connection!
     * @param sql
     */
    protected boolean _execute( String sql, List<Object> values ){
        Function<String, String> exceptionMessageCreator = (s)->{
            String[] parts = s.split(" \\? ");
            String joined = String.join(",",values.stream().map(v->"'"+v.toString()+"'").collect(Collectors.toList()));
            return parts[0]+joined+parts[parts.length-1];
        };

        Connection conn = _connections.get(Thread.currentThread());
        if ( values!=null ){
            try {
                PreparedStatement pstmt = _newPreparedStatement(sql, values);
                try {
                    boolean state = pstmt.execute();
                    pstmt.close();
                } catch (SQLException e) {
                    pstmt.close();
                    return false;
                }
            } catch (SQLException e) {
                return false;
            }
            return true;
        }

        try {
            Statement stmt = conn.createStatement();
            try {
                stmt.execute(sql);
                stmt.close();
                return true;
            } catch (SQLException e) {
                stmt.close();
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Converts a ResultSet into a JSON Object.
     * It can be converted to a String and is sent
     * to the client when requested (Ajax).
     * @param rs
     * @return
     * @throws SQLException
     * @throws JSONException
     */
    protected static JSONArray _toJSON( ResultSet rs ) throws SQLException, JSONException
    {
        JSONArray json = new JSONArray();
        ResultSetMetaData rsmd = rs.getMetaData();

        while( rs.next() ) {
            int numColumns = rsmd.getColumnCount();
            JSONObject jo = new JSONObject();

            for (int i=1; i<numColumns+1; i++)
            {
                String column_name = rsmd.getColumnName(i);

                if(rsmd.getColumnType(i)==java.sql.Types.ARRAY){
                    jo.put(column_name, rs.getArray(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.BIGINT){
                    jo.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.BOOLEAN){
                    jo.put(column_name, rs.getBoolean(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.BLOB){
                    jo.put(column_name, rs.getBlob(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.DOUBLE){
                    jo.put(column_name, rs.getDouble(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.FLOAT){
                    jo.put(column_name, rs.getFloat(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.INTEGER){
                    jo.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.NVARCHAR){
                    jo.put(column_name, rs.getNString(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.VARCHAR){
                    jo.put(column_name, rs.getString(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.TINYINT){
                    jo.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.SMALLINT){
                    jo.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.DATE){
                    jo.put(column_name, rs.getDate(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.TIMESTAMP){
                    jo.put(column_name, rs.getTimestamp(column_name));
                } else {
                    jo.put(column_name, rs.getObject(column_name));
                }
            }
            json.put(jo);
        }
        rs.close();
        return json;
    }

    protected JSONArray _toCRUD(ResultSet rs, String tableName, String[] tableNames) throws SQLException, JSONException
    {
        JSONArray json = new JSONArray();
        ResultSetMetaData rsmd = rs.getMetaData();

        String relationTable = null;

        for(String t : tableNames){
            String[] words = t.split("_");
            boolean isRelationalTable = false;
            boolean isRelevant = false;
            for(String w : words) if(w.toLowerCase().contains("relation")) isRelationalTable = true;
            for(String w : words) if(w.toLowerCase().contains(tableName)) isRelevant = true;
            if (isRelationalTable && isRelevant && words.length==2) {
                relationTable = t;
            }
        }

        while(rs.next()) {
            int numColumns = rsmd.getColumnCount();
            JSONObject obj = new JSONObject();

            for (int i=1; i<numColumns+1; i++)
            {
                String column_name = rsmd.getColumnName(i);

                if(rsmd.getColumnType(i)==java.sql.Types.ARRAY){
                    obj.put(column_name, rs.getArray(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.BIGINT){
                    obj.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.BOOLEAN){
                    obj.put(column_name, rs.getBoolean(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.BLOB){
                    obj.put(column_name, rs.getBlob(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.DOUBLE){
                    obj.put(column_name, rs.getDouble(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.FLOAT){
                    obj.put(column_name, rs.getFloat(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.INTEGER){
                    obj.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.NVARCHAR){
                    obj.put(column_name, rs.getNString(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.VARCHAR){
                    obj.put(column_name, rs.getString(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.TINYINT){
                    obj.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.SMALLINT){
                    obj.put(column_name, rs.getInt(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.DATE){
                    obj.put(column_name, rs.getDate(column_name));
                }
                else if(rsmd.getColumnType(i)==java.sql.Types.TIMESTAMP){
                    obj.put(column_name, rs.getTimestamp(column_name));
                }
                else{
                    obj.put(column_name, rs.getObject(column_name));
                }
                if(relationTable!=null && obj.get("id")!=null){
                    Object id = obj.get("id");
                    String sql = "SELECT * FROM "+relationTable+" rt "+" WHERE rt.child_tails_id = "+id.toString();
                    final String targTable = relationTable;
                    _for(sql, cs->{
                        try {
                            obj.put("children", _toCRUD(cs, targTable, tableNames));
                        } catch (SQLException throwables) {
                            throwables.printStackTrace();
                        }
                    }, null);

                }
            }
            json.put(obj);
        }
        return json;
    }

    protected void _executeFile(String name){
        String[] commands;
        File file = (name.contains(":"))?new File(name):new File("storage/sql/", name);
        int fileLength = (int) file.length();
        try {
            byte[] fileData = Util.readFileData(file, fileLength);
            String query = new String(fileData);
            commands = query.split("--<#SPLIT#>--");
            for(String command : commands){
                _execute(command);
            }
            _commit();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class Util
    {

        public static byte[] readFileData(File file, int fileLength) throws IOException {
            FileInputStream fileIn = null;
            byte[] fileData = new byte[fileLength];
            try {
                fileIn = new FileInputStream(file);
                fileIn.read(fileData);
            } finally {
                if (fileIn != null)
                    fileIn.close();
            }
            return fileData;
        }

        /**
         * Helper method which reads the file with the given name and returns
         * the contents of this file as a String. Will exit the application
         * if the file can not be read.
         *
         * @param path
         * @return The contents of the file
         */
        public String readResource(String path){
            InputStream stream = this.getClass().getClassLoader().getResourceAsStream(path);
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(stream));
                StringBuffer sb = new StringBuffer();
                String line = "";
                while (line!=null) {
                    line = br.readLine();
                    if (line != null) sb.append(line).append("\n");
                }
                return sb.toString();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
                return null;
            }
        }

    }


    protected boolean doesTableExist(String tableName) {
        String command = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        Map<String, List<Object>> result = _query(command, List.of(tableName));
        return !result.isEmpty();
    }

    protected static String _fromJavaTypeToDBType(Class<?> type) {
        if ( type == Integer.class || type == int.class )
            return "INT";
        else if ( type == String.class)
            return "TEXT";
        else if ( type == Boolean.class || type == boolean.class )
            return "BOOLEAN";
        else if ( type == Double.class || type == double.class )
            return "DOUBLE";
        else if ( type == Float.class || type == float.class )
            return "FLOAT";
        else if ( type == Long.class || type == long.class )
            return "BIGINT";
        else if ( type == Short.class || type == short.class )
            return "SMALLINT";
        else if ( type == Byte.class || type == byte.class )
            return "TINYINT";
        else
            throw new IllegalArgumentException("The type " + type.getName() + " is not supported");
    }

    protected static Class<?> _fromDBTypeToJavaType(String type) {
        return switch (type) {
            case "INT", "INTEGER" -> Integer.class;
            case "TEXT" -> String.class;
            case "BOOLEAN" -> Boolean.class;
            case "DOUBLE" -> Double.class;
            case "FLOAT" -> Float.class;
            case "BIGINT" -> Long.class;
            case "SMALLINT" -> Short.class;
            case "TINYINT" -> Byte.class;
            default -> throw new IllegalArgumentException("The type " + type + " is not supported");
        };
    }


}
