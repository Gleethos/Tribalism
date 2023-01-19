package app;

public class RootViewModel {

    private final ServerViewModel serverViewModel;
    private final ContentViewModel contentViewModel;

    public RootViewModel(AppContext context) {
        this.serverViewModel = new ServerViewModel(context);
        this.contentViewModel = new ContentViewModel(context);
    }

    public ServerViewModel serverViewModel() {
        return serverViewModel;
    }

    public ContentViewModel mainViewModel() {
        return contentViewModel;
    }

}
