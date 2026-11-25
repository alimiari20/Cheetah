import java.net.URI;
import java.net.http.*;
import java.time.Duration;
class ResendProbe {
  public static void main(String[] args) {
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    var request = HttpRequest.newBuilder(URI.create("https://api.resend.com/")).timeout(Duration.ofSeconds(10)).GET().build();
    try { System.out.println("Java GET HTTP " + client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()); }
    catch (Exception ex) { for (Throwable cause = ex; cause != null; cause = cause.getCause()) System.out.println("Java GET exception " + cause.getClass().getSimpleName()); }
  }
}

