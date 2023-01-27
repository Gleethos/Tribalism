package app;

import java.util.Objects;

public class RootViewModel {

    private final AppContext context;
    private final ServerViewModel serverViewModel;

    public RootViewModel(AppContext context) {
        this.context = Objects.requireNonNull(context);
        this.serverViewModel = new ServerViewModel(context);
    }

    public ServerViewModel serverViewModel() {
        return serverViewModel;
    }

    public ContentViewModel createMainViewModel() {
        return new ContentViewModel(context);
    }

}
