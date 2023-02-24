package dal.impl;

import dal.api.DataBase;
import dal.api.DataBaseProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Date;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

abstract class AbstractDataBase implements DataBase {

    private final static Logger _LOG = LoggerFactory.getLogger(AbstractDataBase.class);

    /**
     * Connection settings: URL, User, Password!
     */
    protected boolean _AUTOCOMMIT = true;
    private final String _url, _user, _pwd;

    private final Map<Thread, Connection> _connections = new HashMap<>();
    private final DataBaseProcessor _processor;

    AbstractDataBase(
            String url,
            String name,
            String password,
            DataBaseProcessor processor
    ) {
        var currentThread = Thread.currentThread();
        _processor = processor;
        if ( !_processor.getThreads().contains(currentThread) )
            throw new RuntimeException("The current thread '" + currentThread.getName() + "' is not allowed to access the database!");

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
        _LOG.info("Establishing connection to database url '"+_url+"' now.");
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

    private Connection _getConnection() {
        Connection con = _connections.get(Thread.currentThread());
        if ( con == null && _processor.getThreads().contains(Thread.currentThread()) ) {
            try {
                _createAndOrConnectToDatabase();
                con = _connections.get(Thread.currentThread());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if ( con == null ) {
            String threadName = Thread.currentThread().getName();
            String postfix = "";
            // We check if this thread is in fact the AWT-Event-Thread, if so we use the main thread instead!
            if ( threadName.startsWith("AWT-EventQueue-") )
                postfix = " \nIt looks like you are trying to access the database from the GUI thread! \n" +
                            "Maybe there is a main thread you want to use instead?";

            _LOG.error("No connection found for thread '{}'!"+postfix, threadName);
            throw new RuntimeException("No connection found for thread '"+threadName+"'!"+postfix);
        }
        return con;
    }

    /**
     * Closing Connection!
     */
    protected void _close(){
        try {
            _getConnection().close();
            _connections.put(Thread.currentThread(), null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close(){ _close(); }

    /**
     * Returns a list of all table names of a connection!
     */
    @Override
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
        Map<String, List<String>> space = new LinkedHashMap<>();
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

    private PreparedStatement _newPreparedStatement(String sql, List<? extends Object> values) throws SQLException {
        PreparedStatement pstmt = _getConnection().prepareStatement(sql);
        if ( values != null ) {
            for(int i=0; i<values.size(); i++) pstmt.setObject(i + 1, values.get(i));
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
                    pstmt.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Statement stmt = _getConnection().createStatement();
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
                    throw new RuntimeException(e);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }


    }

    protected Map<String, List<Object>> _query(String sql) {
        return _query(sql, null);
    }

    protected Map<String, List<Object>> _query(String sql, List<Object> values){
        Map<String, List<Object>> result = new LinkedHashMap<>();
        _processor.processNow(()->{
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
        });
        return result;
    }

    /**
     * SQL execution on connection!
     * @param sql
     */
    protected void _execute(String sql) {
        if(sql.isBlank()) return;
        _processor.process(()->{
            Connection conn = _getConnection();
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
                e.printStackTrace();
            }
        });
    }

    /**
     * SQL execution on connection!
     * @param sql - SQL statement to execute
     */
    protected boolean _update( String sql, List<? extends Object> values ){
        return _processor.processNowAndGet(()->{
            Connection conn = _getConnection();
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
        });
    }

    protected boolean doesTableExist(String tableName) {
        String command = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        Map<String, List<Object>> result = _query(command, List.of(tableName));
        return !result.isEmpty();
    }

    protected static String _fromJavaTypeToDBType(Class<?> type) {
        if ( type == Integer.class || type == int.class )
            return "INTEGER";
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


    protected static boolean _isBasicDataType(Class<?> type) {
        return
                type.equals(String.class) ||
                        type.equals(int.class) ||
                        type.equals(boolean.class) ||
                        type.equals(Integer.class) ||
                        type.equals(Boolean.class) ||
                        type.equals(Long.class) ||
                        type.equals(long.class) ||
                        type.equals(Double.class) ||
                        type.equals(double.class) ||
                        type.equals(Float.class) ||
                        type.equals(float.class) ||
                        type.equals(Short.class) ||
                        type.equals(short.class) ||
                        type.equals(Byte.class) ||
                        type.equals(byte.class) ||
                        type.equals(Character.class) ||
                        type.equals(char.class);
    }

    protected static String _tableNameFromClass(Class<?> clazz) {
        return _nameFromClass(clazz) + "_table";
    }

    protected static String _nameFromClass(Class<?> clazz) {
        String tableName = clazz.getName();
        // We replace the package dots with underscores:
        // This is the name of the interface but where the '.' are replaced with '_'
        tableName = tableName.replaceAll("\\.", "_");

        // Let's use regex to check if the name is valid
        if ( !tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*") )
            throw new IllegalArgumentException(
                    "The name of the interface " + clazz.getName() + " is not a valid name for a table"
            );
        return tableName;
    }


}
