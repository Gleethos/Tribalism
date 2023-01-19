import app.AppContext;
import app.RootView;
import app.RootViewModel;
import com.formdev.flatlaf.FlatLightLaf;
import swingtree.UI;

public class Main {

    public static void main( String... args )
    {
        AppContext context = new AppContext();
        FlatLightLaf.setup();
        UI.show(new RootView(new RootViewModel(context)));
    }

}
