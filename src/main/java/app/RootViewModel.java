package app;

import java.util.Objects;

public class RootViewModel
{
    private final AppContext context;
    private final ServerViewModel serverViewModel;
    private final ContentViewModel mainViewModel;
    private final DataBaseViewModel dataBaseViewModel;
    private final BootstrapViewModel bootstrapViewModel;

    public RootViewModel( AppContext context ) {
        this.context = Objects.requireNonNull(context);
        this.serverViewModel = new ServerViewModel(context);
        this.mainViewModel = new ContentViewModel(context);
        this.dataBaseViewModel = new DataBaseViewModel(context.db());
        this.bootstrapViewModel = new BootstrapViewModel(context);
    }

    public boolean developerTabsAreShown() { return context.app().isDevViews(); }

    public ServerViewModel serverViewModel() { return serverViewModel; }

    public ContentViewModel mainViewModel() { return mainViewModel; }

    public DataBaseViewModel dataBaseViewModel() { return dataBaseViewModel; }

    public BootstrapViewModel bootstrapViewModel() { return bootstrapViewModel; }
}
