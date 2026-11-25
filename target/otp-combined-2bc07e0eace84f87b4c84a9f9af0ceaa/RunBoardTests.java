import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
class RunBoardTests {
    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass("model.BoardFactoryTest")).build();
        LauncherFactory.create().execute(request, listener);
        var result = listener.getSummary();
        result.printTo(new java.io.PrintWriter(System.out, true));
        result.printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (result.getTestsSucceededCount() != 8 || result.getTotalFailureCount() != 0) System.exit(1);
    }
}
