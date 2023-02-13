package app;

import java.util.Objects;

public class RootViewModel
{
    private final AppContext context;
    private final ServerViewModel serverViewModel;
    private final ContentViewModel mainViewModel;
    private final DataBaseViewModel dataBaseViewModel;

    public RootViewModel(AppContext context) {
        this.context = Objects.requireNonNull(context);
        this.serverViewModel = new ServerViewModel(context);
        this.mainViewModel = new ContentViewModel(context);
        this.dataBaseViewModel = new DataBaseViewModel(context.db());
    }

    public ServerViewModel serverViewModel() {
        return serverViewModel;
    }

    public ContentViewModel createMainViewModel() {
        return mainViewModel;
    }

    public DataBaseViewModel createDataBaseViewModel() {
        return dataBaseViewModel;
    }

}
