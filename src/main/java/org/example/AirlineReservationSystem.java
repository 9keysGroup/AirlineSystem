package org.example;

public class AirlineReservationSystem {

    public static void main(String[] args) throws Exception {
        int port = resolvePort();
        AirlineWebServer server = new AirlineWebServer(port);
        server.start();
    }

    private static int resolvePort() {
        String rawPort = System.getenv("PORT");
        if (rawPort == null || rawPort.trim().isEmpty()) {
            return 8080;
        }

        try {
            return Integer.parseInt(rawPort.trim());
        } catch (NumberFormatException ignored) {
            return 8080;
        }
    }
}
