import app.App;
import com.beust.jcommander.JCommander;

/**
 *  The main class which is used to start the application when exported as a JAR file.
 *  The packaged Tribalism app can be started from the command-line like this: <br>
 *  <code>java -jar tribalism.jar --at /home/user/tribalism.db</code>
 */
public final class Main
{
    public static void main( String... args )
    {
        App app = new App();
        JCommander.newBuilder()
                .addObject(app)
                .build()
                .parse(args);

        app.run(); // Start the application business logic, note that this is blocking.
    }
}
