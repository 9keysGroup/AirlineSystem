package org.example;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class AirlineWebServer {
    private static final String ASSET_VERSION = "20260413b";
    private final AirlineSystem airlineSystem;
    private final HttpServer server;

    public AirlineWebServer(int port) throws IOException {
        this.airlineSystem = new AirlineSystem();
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        registerRoutes();
    }

    public void start() {
        server.start();
        System.out.println("Airline web UI running on port " + server.getAddress().getPort());
    }

    private void registerRoutes() {
        server.createContext("/", new TextHandler("text/html; charset=utf-8", pageHtml()));
        server.createContext("/health", new TextHandler("text/plain; charset=utf-8", "OK"));
        server.createContext("/app.css", new TextHandler("text/css; charset=utf-8", pageCss()));
        server.createContext("/app.js", new TextHandler("application/javascript; charset=utf-8", pageJs()));
        server.createContext("/api/cities", new ApiHandler() {
            @Override
            protected String handleRequest(HttpExchange exchange) {
                List<String> cities = airlineSystem.getCities();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < cities.size(); i++) {
                    if (i > 0) {
                        json.append(",");
                    }
                    json.append(quote(cities.get(i)));
                }
                json.append("]");
                return json.toString();
            }
        });
        server.createContext("/api/network", new ApiHandler() {
            @Override
            protected String handleRequest(HttpExchange exchange) {
                AirlineSystem.FlightGraph graph = airlineSystem.getNetwork();
                StringBuilder json = new StringBuilder("{\"cities\":[");
                int cityIndex = 0;
                for (String city : graph.getCities()) {
                    if (cityIndex++ > 0) {
                        json.append(",");
                    }
                    json.append("{\"name\":").append(quote(city)).append(",\"flights\":[");
                    List<AirlineSystem.Flight> flights = graph.getFlights(city);
                    for (int i = 0; i < flights.size(); i++) {
                        AirlineSystem.Flight flight = flights.get(i);
                        if (i > 0) {
                            json.append(",");
                        }
                        json.append("{")
                                .append("\"destination\":").append(quote(flight.getDestination())).append(",")
                                .append("\"cost\":").append(flight.getCost()).append(",")
                                .append("\"duration\":").append(flight.getDuration())
                                .append("}");
                    }
                    json.append("]}");
                }
                json.append("]}");
                return json.toString();
            }
        });
        server.createContext("/api/route", new ApiHandler() {
            @Override
            protected String handleRequest(HttpExchange exchange) {
                Map<String, String> params = queryParams(exchange);
                String from = params.get("from");
                String to = params.get("to");
                String mode = params.get("mode");

                if (isBlank(from) || isBlank(to) || from.equals(to)) {
                    return "{\"ok\":false,\"message\":\"Choose two different cities.\"}";
                }

                boolean byCost = !"time".equalsIgnoreCase(mode);
                AirlineSystem.RouteResult result = airlineSystem.findRoute(from, to, byCost);
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"ok\":true,");
                json.append("\"mode\":").append(quote(byCost ? "cost" : "time")).append(",");
                json.append("\"found\":").append(result.isFound()).append(",");
                json.append("\"formatted\":").append(quote(result.formatForDisplay())).append(",");
                json.append("\"totalCost\":").append(result.getTotalCost()).append(",");
                json.append("\"totalDuration\":").append(result.getTotalDuration()).append(",");
                json.append("\"stops\":").append(Math.max(0, result.getPath().size() - 2)).append(",");
                json.append("\"path\":[");
                List<String> path = result.getPath();
                for (int i = 0; i < path.size(); i++) {
                    if (i > 0) {
                        json.append(",");
                    }
                    json.append(quote(path.get(i)));
                }
                json.append("]}");
                return json.toString();
            }
        });
        server.createContext("/api/bookings", new ApiHandler() {
            @Override
            protected String handleRequest(HttpExchange exchange) {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    return addBooking(exchange);
                }
                return bookingsJson();
            }

            private String addBooking(HttpExchange exchange) {
                Map<String, String> params = queryParams(exchange);
                String name = params.get("name");
                String from = params.get("from");
                String to = params.get("to");
                int tier = parseTier(params.get("tier"));

                if (isBlank(name)) {
                    return "{\"ok\":false,\"message\":\"Passenger name is required.\"}";
                }
                if (isBlank(from) || isBlank(to) || from.equals(to)) {
                    return "{\"ok\":false,\"message\":\"Choose two different cities.\"}";
                }

                airlineSystem.addBooking(name.trim(), tier, from, to);
                return "{\"ok\":true,\"message\":\"Booking added.\",\"queueSize\":" + airlineSystem.getQueueSize() + "}";
            }
        });
        server.createContext("/api/bookings/process", new ApiHandler() {
            @Override
            protected String handleRequest(HttpExchange exchange) {
                AirlineSystem.Booking booking = airlineSystem.processNextBooking();
                if (booking == null) {
                    return "{\"ok\":false,\"message\":\"No bookings are waiting in the queue.\"}";
                }
                return "{"
                        + "\"ok\":true,"
                        + "\"message\":" + quote("Processed " + booking.getPassengerName() + " (" + booking.getTierName() + ")") + ","
                        + "\"booking\":" + bookingJson(booking)
                        + "}";
            }
        });
        server.createContext("/api/benchmark", new ApiHandler() {
            @Override
            protected String handleRequest(HttpExchange exchange) {
                List<AirlineSystem.BenchmarkResult> results = airlineSystem.runPerformanceBenchmark();
                StringBuilder json = new StringBuilder("{\"results\":[");
                for (int i = 0; i < results.size(); i++) {
                    AirlineSystem.BenchmarkResult result = results.get(i);
                    if (i > 0) {
                        json.append(",");
                    }
                    json.append("{")
                            .append("\"cityCount\":").append(result.getCityCount()).append(",")
                            .append("\"edgeCount\":").append(result.getEdgeCount()).append(",")
                            .append("\"elapsedMs\":").append(result.getElapsedMs())
                            .append("}");
                }
                json.append("]}");
                return json.toString();
            }
        });
    }

    private String bookingsJson() {
        List<AirlineSystem.Booking> bookings = airlineSystem.getQueuedBookings();
        StringBuilder json = new StringBuilder();
        json.append("{\"queueSize\":").append(airlineSystem.getQueueSize()).append(",\"bookings\":[");
        for (int i = 0; i < bookings.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(bookingJson(bookings.get(i)));
        }
        json.append("]}");
        return json.toString();
    }

    private String bookingJson(AirlineSystem.Booking booking) {
        return "{"
                + "\"name\":" + quote(booking.getPassengerName()) + ","
                + "\"tier\":" + quote(booking.getTierName()) + ","
                + "\"from\":" + quote(booking.getFrom()) + ","
                + "\"to\":" + quote(booking.getTo())
                + "}";
    }

    private Map<String, String> queryParams(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        return parseQuery(rawQuery == null ? "" : rawQuery);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        if (rawQuery.isEmpty()) {
            return params;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    private int parseTier(String tierValue) {
        try {
            int tier = Integer.parseInt(tierValue);
            if (tier >= 0 && tier < AirlineSystem.Booking.TIER_NAMES.length) {
                return tier;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(c);
            }
        }
        escaped.append("\"");
        return escaped.toString();
    }

    private abstract class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");
            headers.set("Cache-Control", "no-store");
            headers.set("Pragma", "no-cache");
            headers.set("Expires", "0");

            String response = handleRequest(exchange);
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            OutputStream output = exchange.getResponseBody();
            output.write(body);
            output.close();
        }

        protected abstract String handleRequest(HttpExchange exchange);
    }

    private static class TextHandler implements HttpHandler {
        private final String contentType;
        private final String content;

        private TextHandler(String contentType, String content) {
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = content.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            exchange.sendResponseHeaders(200, body.length);
            OutputStream output = exchange.getResponseBody();
            output.write(body);
            output.close();
        }
    }

    private String pageHtml() {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>Airline Reservation System</title>\n"
                + "  <link rel=\"stylesheet\" href=\"/app.css?v=" + ASSET_VERSION + "\">\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div class=\"page-shell\">\n"
                + "    <header class=\"hero\">\n"
                + "      <p class=\"eyebrow\">Local Web UI</p>\n"
                + "      <h1>Airline Reservation System</h1>\n"
                + "      <p class=\"subtitle\">Search routes, manage bookings, inspect the network, and benchmark Dijkstra from the browser.</p>\n"
                + "    </header>\n"
                + "    <main class=\"dashboard\">\n"
                + "      <section class=\"card route-card\">\n"
                + "        <div class=\"card-head\">\n"
                + "          <h2>Route Finder</h2>\n"
                + "          <span class=\"section-kicker\">Live path output</span>\n"
                + "        </div>\n"
                + "        <div class=\"toolbar-row\">\n"
                + "          <div class=\"segmented\">\n"
                + "            <label><input type=\"radio\" name=\"mode\" value=\"cost\" checked> Cheapest</label>\n"
                + "            <label><input type=\"radio\" name=\"mode\" value=\"time\"> Fastest</label>\n"
                + "          </div>\n"
                + "        </div>\n"
                + "        <div class=\"form-grid\">\n"
                + "          <label>From<select id=\"route-from\"></select></label>\n"
                + "          <label>To<select id=\"route-to\"></select></label>\n"
                + "        </div>\n"
                + "        <div id=\"route-output\" class=\"route-output empty\">\n"
                + "          <div class=\"placeholder-title\">Route details will appear here</div>\n"
                + "          <p>Select two cities, choose cheapest or fastest, and run a search.</p>\n"
                + "        </div>\n"
                + "      </section>\n"
                + "      <section class=\"card booking-card\">\n"
                + "        <div class=\"card-head\">\n"
                + "          <h2>Booking Queue</h2>\n"
                + "          <span id=\"queue-size\" class=\"pill\">Queued bookings: 0</span>\n"
                + "        </div>\n"
                + "        <div class=\"booking-form\">\n"
                + "          <label>Passenger<input id=\"passenger-name\" type=\"text\" placeholder=\"Name\"></label>\n"
                + "          <label>Tier<select id=\"booking-tier\"></select></label>\n"
                + "          <label>From<select id=\"booking-from\"></select></label>\n"
                + "          <label>To<select id=\"booking-to\"></select></label>\n"
                + "        </div>\n"
                + "        <div class=\"actions\">\n"
                + "          <button id=\"add-booking\" class=\"primary\">Add Booking</button>\n"
                + "          <button id=\"process-booking\" class=\"ghost\">Process Next</button>\n"
                + "        </div>\n"
                + "        <div id=\"booking-message\" class=\"inline-message muted\">Queue updates appear here.</div>\n"
                + "        <div class=\"table-wrap\">\n"
                + "          <table>\n"
                + "            <thead><tr><th>Passenger</th><th>Tier</th><th>From</th><th>To</th></tr></thead>\n"
                + "            <tbody id=\"booking-table\"><tr><td colspan=\"4\">Loading bookings...</td></tr></tbody>\n"
                + "          </table>\n"
                + "        </div>\n"
                + "      </section>\n"
                + "      <section class=\"card network-card\">\n"
                + "        <div class=\"card-head\">\n"
                + "          <h2>Flight Network</h2>\n"
                + "          <span class=\"section-kicker\">Connected city map</span>\n"
                + "        </div>\n"
                + "        <div id=\"network-output\" class=\"network-grid\"><div class=\"network-empty\">Loading city network...</div></div>\n"
                + "      </section>\n"
                + "      <section class=\"card benchmark-card\">\n"
                + "        <div class=\"card-head\">\n"
                + "          <h2>Performance</h2>\n"
                + "          <button id=\"run-benchmark\" class=\"ghost\">Run Benchmark</button>\n"
                + "        </div>\n"
                + "        <div id=\"benchmark-output\" class=\"benchmark-shell empty\">\n"
                + "          <div class=\"placeholder-title\">Benchmark results are waiting</div>\n"
                + "          <p>Run the benchmark to compare runtime across graph sizes.</p>\n"
                + "        </div>\n"
                + "      </section>\n"
                + "    </main>\n"
                + "  </div>\n"
                + "  <script src=\"/app.js?v=" + ASSET_VERSION + "\"></script>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String pageCss() {
        return ":root {\n"
                + "  --bg: #efe9dc;\n"
                + "  --panel: rgba(255, 250, 244, 0.92);\n"
                + "  --panel-strong: #fffaf2;\n"
                + "  --ink: #1b2628;\n"
                + "  --muted: #617073;\n"
                + "  --accent: #0a7b70;\n"
                + "  --accent-2: #f08c00;\n"
                + "  --accent-3: #143b5d;\n"
                + "  --good: #1f8f60;\n"
                + "  --warn: #a25a00;\n"
                + "  --line: rgba(27, 38, 40, 0.12);\n"
                + "  --shadow: 0 22px 60px rgba(31, 42, 44, 0.12);\n"
                + "}\n"
                + "* { box-sizing: border-box; }\n"
                + "body { margin: 0; font-family: Georgia, 'Times New Roman', serif; color: var(--ink); background: radial-gradient(circle at top left, rgba(240, 140, 0, 0.18), transparent 28%), radial-gradient(circle at top right, rgba(10, 123, 112, 0.18), transparent 24%), linear-gradient(180deg, #fbf7f0 0%, #ebe2d3 100%); min-height: 100vh; }\n"
                + ".page-shell { max-width: 1380px; margin: 0 auto; padding: 32px 20px 40px; }\n"
                + ".hero { padding: 30px 32px; border-radius: 30px; background: linear-gradient(135deg, rgba(255,250,244,0.96), rgba(255,241,214,0.96)); box-shadow: var(--shadow); border: 1px solid rgba(255,255,255,0.55); position: relative; overflow: hidden; }\n"
                + ".hero::after { content: ''; position: absolute; width: 220px; height: 220px; right: -40px; top: -80px; background: radial-gradient(circle, rgba(10,123,112,0.20), transparent 62%); }\n"
                + ".eyebrow { margin: 0 0 8px; text-transform: uppercase; letter-spacing: 0.22em; color: var(--accent); font-size: 12px; }\n"
                + ".hero h1 { margin: 0; font-size: clamp(34px, 5vw, 64px); line-height: 0.95; }\n"
                + ".subtitle { max-width: 780px; margin: 14px 0 10px; color: var(--muted); font-size: 18px; }\n"
                + ".hint { margin: 0; color: var(--muted); }\n"
                + ".hint span { color: var(--ink); font-weight: 700; }\n"
                + ".dashboard { display: grid; grid-template-columns: 1.1fr 1fr; gap: 18px; margin-top: 22px; }\n"
                + ".card { background: var(--panel); border-radius: 24px; padding: 22px; box-shadow: var(--shadow); border: 1px solid rgba(255,255,255,0.5); }\n"
                + ".route-card, .booking-card { min-height: 420px; }\n"
                + ".network-card, .benchmark-card { min-height: 320px; }\n"
                + ".card-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 14px; }\n"
                + ".card-head h2 { margin: 0; font-size: 26px; }\n"
                + ".section-kicker { font-size: 12px; letter-spacing: 0.18em; text-transform: uppercase; color: var(--accent-3); }\n"
                + ".toolbar-row { display: flex; justify-content: space-between; align-items: center; gap: 14px; flex-wrap: wrap; margin-bottom: 12px; }\n"
                + ".form-grid, .booking-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }\n"
                + "label { display: grid; gap: 8px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.1em; color: var(--muted); }\n"
                + "select, input { width: 100%; border: 1px solid var(--line); border-radius: 14px; padding: 12px 14px; font: inherit; color: var(--ink); background: rgba(255,255,255,0.9); }\n"
                + "button { border: 0; border-radius: 999px; padding: 12px 18px; font: inherit; cursor: pointer; transition: transform 120ms ease, opacity 120ms ease, box-shadow 120ms ease; }\n"
                + "button:hover { transform: translateY(-1px); }\n"
                + ".primary { background: var(--accent); color: #fff; margin-top: 16px; box-shadow: 0 10px 24px rgba(10, 123, 112, 0.22); }\n"
                + ".compact { margin-top: 0; }\n"
                + ".ghost { background: rgba(14, 122, 109, 0.08); color: var(--accent); }\n"
                + ".actions { display: flex; gap: 10px; margin: 16px 0 10px; }\n"
                + ".segmented { display: flex; gap: 10px; flex-wrap: wrap; }\n"
                + ".segmented label { display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 999px; background: rgba(10, 123, 112, 0.08); color: var(--ink); letter-spacing: 0; text-transform: none; }\n"
                + ".pill { display: inline-flex; align-items: center; padding: 8px 12px; border-radius: 999px; background: rgba(241, 143, 1, 0.15); color: #8a4f00; font-size: 13px; font-weight: 700; }\n"
                + ".table-wrap { overflow: auto; border-radius: 18px; border: 1px solid var(--line); }\n"
                + "table { width: 100%; border-collapse: collapse; }\n"
                + "th, td { padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--line); }\n"
                + "th { background: rgba(14, 122, 109, 0.08); font-size: 13px; text-transform: uppercase; letter-spacing: 0.08em; }\n"
                + ".inline-message { min-height: 20px; font-size: 14px; margin-bottom: 10px; padding: 10px 12px; border-radius: 14px; background: rgba(20, 59, 93, 0.06); }\n"
                + ".inline-message.muted { color: var(--muted); }\n"
                + ".inline-message.success { color: var(--good); background: rgba(31, 143, 96, 0.10); }\n"
                + ".inline-message.warning { color: var(--warn); background: rgba(240, 140, 0, 0.12); }\n"
                + ".route-output, .benchmark-shell { margin-top: 16px; min-height: 220px; border-radius: 22px; padding: 18px; background: linear-gradient(180deg, #102126 0%, #182e34 100%); color: #f1f7f5; border: 1px solid rgba(255,255,255,0.08); }\n"
                + ".route-output.empty, .benchmark-shell.empty { display: grid; place-items: center; text-align: center; color: rgba(241,247,245,0.76); }\n"
                + ".placeholder-title { font-size: 22px; margin-bottom: 8px; }\n"
                + ".route-mode-badge { display: inline-flex; padding: 7px 10px; border-radius: 999px; background: rgba(240, 140, 0, 0.18); color: #ffd79d; font-size: 12px; text-transform: uppercase; letter-spacing: 0.12em; }\n"
                + ".route-topline { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }\n"
                + ".route-title { font-size: 28px; margin: 12px 0 6px; }\n"
                + ".route-path { display: flex; flex-wrap: wrap; gap: 10px; margin: 18px 0 20px; }\n"
                + ".path-city { display: inline-flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 999px; background: rgba(255,255,255,0.10); }\n"
                + ".path-arrow { color: #73d2c7; align-self: center; }\n"
                + ".metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }\n"
                + ".metric-card { padding: 14px; border-radius: 18px; background: rgba(255,255,255,0.08); }\n"
                + ".metric-label { display: block; font-size: 11px; text-transform: uppercase; letter-spacing: 0.16em; color: rgba(241,247,245,0.64); margin-bottom: 8px; }\n"
                + ".metric-value { font-size: 24px; }\n"
                + ".network-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 14px; }\n"
                + ".network-empty { grid-column: 1 / -1; padding: 20px; border-radius: 18px; background: rgba(10, 123, 112, 0.06); color: var(--muted); text-align: center; }\n"
                + ".network-city { background: var(--panel-strong); border: 1px solid var(--line); border-radius: 20px; padding: 16px; }\n"
                + ".network-city h3 { margin: 0 0 12px; font-size: 22px; }\n"
                + ".flight-list { display: grid; gap: 10px; }\n"
                + ".flight-chip { display: grid; gap: 4px; padding: 12px; border-radius: 16px; background: rgba(10, 123, 112, 0.06); }\n"
                + ".flight-destination { font-weight: 700; }\n"
                + ".flight-meta { color: var(--muted); font-size: 14px; }\n"
                + ".benchmark-shell { background: linear-gradient(180deg, rgba(255,250,244,0.92), rgba(255,244,230,0.92)); color: var(--ink); border-color: var(--line); }\n"
                + ".benchmark-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }\n"
                + ".benchmark-stat { padding: 14px; border-radius: 18px; background: rgba(20,59,93,0.06); }\n"
                + ".benchmark-chart { display: grid; gap: 10px; margin-bottom: 16px; }\n"
                + ".benchmark-bar-row { display: grid; grid-template-columns: 84px 1fr 70px; gap: 12px; align-items: center; }\n"
                + ".benchmark-bar-label, .benchmark-bar-value { font-size: 13px; color: var(--muted); }\n"
                + ".benchmark-bar-track { height: 12px; border-radius: 999px; background: rgba(20,59,93,0.10); overflow: hidden; }\n"
                + ".benchmark-bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #0a7b70, #f08c00); }\n"
                + ".benchmark-table { width: 100%; border-collapse: collapse; }\n"
                + ".benchmark-table th, .benchmark-table td { padding: 12px 10px; border-bottom: 1px solid var(--line); }\n"
                + ".benchmark-table th { text-align: left; font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.12em; }\n"
                + ".benchmark-table tr:last-child td { border-bottom: 0; }\n"
                + "@media (max-width: 980px) { .dashboard { grid-template-columns: 1fr; } .form-grid, .booking-form, .metric-grid, .benchmark-summary { grid-template-columns: 1fr; } .toolbar-row { align-items: stretch; } }\n";
    }

    private String pageJs() {
        return "(function () {\n"
                + "  const tiers = ['Regular', 'Silver', 'Gold', 'Platinum'];\n"
                + "  const routeFrom = document.getElementById('route-from');\n"
                + "  const routeTo = document.getElementById('route-to');\n"
                + "  const bookingFrom = document.getElementById('booking-from');\n"
                + "  const bookingTo = document.getElementById('booking-to');\n"
                + "  const bookingTier = document.getElementById('booking-tier');\n"
                + "  const routeOutput = document.getElementById('route-output');\n"
                + "  const networkOutput = document.getElementById('network-output');\n"
                + "  const benchmarkOutput = document.getElementById('benchmark-output');\n"
                + "  const bookingTable = document.getElementById('booking-table');\n"
                + "  const queueSize = document.getElementById('queue-size');\n"
                + "  const bookingMessage = document.getElementById('booking-message');\n"
                + "  function option(value, label) { const el = document.createElement('option'); el.value = value; el.textContent = label || value; return el; }\n"
                + "  function selectedMode() { return document.querySelector('input[name=\"mode\"]:checked').value; }\n"
                + "  function query(params) { return Object.keys(params).map(function (key) { return encodeURIComponent(key) + '=' + encodeURIComponent(params[key]); }).join('&'); }\n"
                + "  function escapeHtml(value) { return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\\\"/g, '&quot;'); }\n"
                + "  function fetchJson(url, options) { return fetch(url, options).then(function (response) { if (!response.ok) { throw new Error('Request failed: ' + response.status); } return response.json(); }); }\n"
                + "  function setMessage(text, variant) { bookingMessage.className = 'inline-message ' + (variant || 'muted'); bookingMessage.textContent = text; }\n"
                + "  function renderBookings(data) { queueSize.textContent = 'Queued bookings: ' + data.queueSize; bookingTable.innerHTML = ''; if (!data.bookings.length) { bookingTable.innerHTML = '<tr><td colspan=\"4\">No bookings in the queue.</td></tr>'; return; } data.bookings.forEach(function (booking) { const row = document.createElement('tr'); row.innerHTML = '<td>' + escapeHtml(booking.name) + '</td>' + '<td>' + escapeHtml(booking.tier) + '</td>' + '<td>' + escapeHtml(booking.from) + '</td>' + '<td>' + escapeHtml(booking.to) + '</td>'; bookingTable.appendChild(row); }); }\n"
                + "  function renderRoute(data) { if (!data.ok || !data.found) { routeOutput.className = 'route-output empty'; routeOutput.innerHTML = '<div><div class=\"placeholder-title\">No route found</div><p>' + escapeHtml(data.message || data.formatted) + '</p></div>'; return; } const pathHtml = data.path.map(function (city, index) { const cityHtml = '<span class=\"path-city\">' + escapeHtml(city) + '</span>'; return index === data.path.length - 1 ? cityHtml : cityHtml + '<span class=\"path-arrow\">-></span>'; }).join(''); const routeSummary = data.stops === 0 ? 'Direct flight' : data.stops + (data.stops === 1 ? ' stop itinerary' : ' stops itinerary'); routeOutput.className = 'route-output'; routeOutput.innerHTML = '<div class=\"route-topline\"><span class=\"route-mode-badge\">' + (data.mode === 'cost' ? 'Cheapest route' : 'Fastest route') + '</span><span>' + escapeHtml(data.path[0]) + ' to ' + escapeHtml(data.path[data.path.length - 1]) + '</span></div><div class=\"route-title\">' + routeSummary + '</div><div class=\"route-path\">' + pathHtml + '</div><div class=\"metric-grid\"><div class=\"metric-card\"><span class=\"metric-label\">Total Cost</span><div class=\"metric-value\">$' + data.totalCost + '</div></div><div class=\"metric-card\"><span class=\"metric-label\">Total Time</span><div class=\"metric-value\">' + data.totalDuration + ' min</div></div><div class=\"metric-card\"><span class=\"metric-label\">Stops</span><div class=\"metric-value\">' + data.stops + '</div></div></div>'; }\n"
                + "  function renderNetwork(data) { if (!data.cities.length) { networkOutput.innerHTML = '<div class=\"network-empty\">No cities are available in the network.</div>'; return; } networkOutput.innerHTML = data.cities.map(function (city) { const flights = city.flights.map(function (flight) { return '<div class=\"flight-chip\"><div class=\"flight-destination\">' + escapeHtml(flight.destination) + '</div><div class=\"flight-meta\">$' + flight.cost + ' | ' + flight.duration + ' min</div></div>'; }).join(''); return '<article class=\"network-city\"><h3>' + escapeHtml(city.name) + '</h3><div class=\"flight-list\">' + flights + '</div></article>'; }).join(''); }\n"
                + "  function renderBenchmark(data) { const longest = data.results.reduce(function (best, item) { return item.elapsedMs > best.elapsedMs ? item : best; }, data.results[0]); const largest = data.results[data.results.length - 1]; const avg = Math.round(data.results.reduce(function (sum, item) { return sum + item.elapsedMs; }, 0) / data.results.length); const maxRuntime = Math.max.apply(null, data.results.map(function (item) { return item.elapsedMs; })); const bars = data.results.map(function (result) { const width = maxRuntime === 0 ? 0 : Math.max(6, Math.round((result.elapsedMs / maxRuntime) * 100)); return '<div class=\"benchmark-bar-row\"><div class=\"benchmark-bar-label\">' + result.cityCount + ' cities</div><div class=\"benchmark-bar-track\"><div class=\"benchmark-bar-fill\" style=\"width:' + width + '%\"></div></div><div class=\"benchmark-bar-value\">' + result.elapsedMs + ' ms</div></div>'; }).join(''); benchmarkOutput.className = 'benchmark-shell'; benchmarkOutput.innerHTML = '<div class=\"benchmark-summary\"><div class=\"benchmark-stat\"><span class=\"metric-label\">Average Runtime</span><div class=\"metric-value\">' + avg + ' ms</div></div><div class=\"benchmark-stat\"><span class=\"metric-label\">Largest Graph</span><div class=\"metric-value\">' + largest.cityCount + ' cities</div></div><div class=\"benchmark-stat\"><span class=\"metric-label\">Peak Runtime</span><div class=\"metric-value\">' + longest.elapsedMs + ' ms</div></div></div><div class=\"benchmark-chart\">' + bars + '</div><table class=\"benchmark-table\"><thead><tr><th>Cities</th><th>Edges</th><th>Runtime</th></tr></thead><tbody>' + data.results.map(function (result) { return '<tr><td>' + result.cityCount + '</td><td>' + result.edgeCount + '</td><td>' + result.elapsedMs + ' ms</td></tr>'; }).join('') + '</tbody></table>'; }\n"
                + "  function runRouteSearch() { if (!routeFrom.value || !routeTo.value) { routeOutput.className = 'route-output empty'; routeOutput.innerHTML = '<div><div class=\"placeholder-title\">Pick two cities</div><p>Select both origin and destination to see a route.</p></div>'; return Promise.resolve(); } if (routeFrom.value === routeTo.value) { routeOutput.className = 'route-output empty'; routeOutput.innerHTML = '<div><div class=\"placeholder-title\">Choose different cities</div><p>Origin and destination cannot be the same.</p></div>'; return Promise.resolve(); } routeOutput.className = 'route-output empty'; routeOutput.innerHTML = '<div><div class=\"placeholder-title\">Computing route</div><p>Calculating the selected path.</p></div>'; const params = query({ from: routeFrom.value, to: routeTo.value, mode: selectedMode() }); return fetchJson('/api/route?' + params).then(function (data) { renderRoute(data); }).catch(function () { routeOutput.className = 'route-output empty'; routeOutput.innerHTML = '<div><div class=\"placeholder-title\">Route unavailable</div><p>The server could not return a route right now.</p></div>'; }); }\n"
                + "  function loadCities() { routeFrom.innerHTML = ''; routeTo.innerHTML = ''; bookingFrom.innerHTML = ''; bookingTo.innerHTML = ''; routeFrom.appendChild(option('', 'Loading cities...')); routeTo.appendChild(option('', 'Loading cities...')); bookingFrom.appendChild(option('', 'Loading cities...')); bookingTo.appendChild(option('', 'Loading cities...')); return fetchJson('/api/cities').then(function (cities) { [routeFrom, routeTo].forEach(function (select, index) { select.innerHTML = ''; select.appendChild(option('', index === 0 ? 'Select origin' : 'Select destination')); cities.forEach(function (city) { select.appendChild(option(city)); }); }); [bookingFrom, bookingTo].forEach(function (select, index) { select.innerHTML = ''; select.appendChild(option('', index === 0 ? 'Select origin' : 'Select destination')); cities.forEach(function (city) { select.appendChild(option(city)); }); }); routeOutput.className = 'route-output empty'; routeOutput.innerHTML = '<div><div class=\"placeholder-title\">Pick two cities</div><p>Select both origin and destination to see a route.</p></div>'; }).catch(function () { [routeFrom, routeTo, bookingFrom, bookingTo].forEach(function (select) { select.innerHTML = ''; select.appendChild(option('', 'Unable to load cities')); }); routeOutput.className = 'route-output empty'; routeOutput.innerHTML = '<div><div class=\"placeholder-title\">Cities unavailable</div><p>The city list could not be loaded from the server.</p></div>'; }); }\n"
                + "  function loadNetwork() { networkOutput.innerHTML = '<div class=\"network-empty\">Loading city network...</div>'; return fetchJson('/api/network').then(function (data) { renderNetwork(data); }).catch(function () { networkOutput.innerHTML = '<div class=\"network-empty\">Unable to load the flight network.</div>'; }); }\n"
                + "  function loadBookings() { bookingTable.innerHTML = '<tr><td colspan=\"4\">Loading bookings...</td></tr>'; return fetchJson('/api/bookings').then(renderBookings).catch(function () { queueSize.textContent = 'Queued bookings: -'; bookingTable.innerHTML = '<tr><td colspan=\"4\">Unable to load bookings.</td></tr>'; setMessage('The booking queue could not be loaded.', 'warning'); }); }\n"
                + "  function loadBenchmark() { benchmarkOutput.className = 'benchmark-shell empty'; benchmarkOutput.innerHTML = '<div><div class=\"placeholder-title\">Loading benchmark</div><p>Collecting runtime data from the server.</p></div>'; return fetchJson('/api/benchmark').then(function (data) { renderBenchmark(data); }).catch(function () { benchmarkOutput.className = 'benchmark-shell empty'; benchmarkOutput.innerHTML = '<div><div class=\"placeholder-title\">Benchmark unavailable</div><p>The benchmark results could not be loaded.</p></div>'; }); }\n"
                + "  function populateTiers() { tiers.forEach(function (tier, index) { const el = option(tier); el.value = String(index); bookingTier.appendChild(el); }); }\n"
                + "  Array.prototype.forEach.call(document.querySelectorAll('input[name=\"mode\"]'), function (input) { input.addEventListener('change', runRouteSearch); });\n"
                + "  routeFrom.addEventListener('change', runRouteSearch); routeTo.addEventListener('change', runRouteSearch);\n"
                + "  document.getElementById('add-booking').addEventListener('click', function () { const name = document.getElementById('passenger-name').value.trim(); const params = query({ name: name, tier: bookingTier.value, from: bookingFrom.value, to: bookingTo.value }); fetchJson('/api/bookings?' + params, { method: 'POST' }).then(function (data) { setMessage(data.message, data.ok ? 'success' : 'warning'); if (data.ok) { document.getElementById('passenger-name').value = ''; return loadBookings(); } }).catch(function () { setMessage('The booking request could not be sent.', 'warning'); }); });\n"
                + "  document.getElementById('process-booking').addEventListener('click', function () { fetchJson('/api/bookings/process').then(function (data) { setMessage(data.message, data.ok ? 'success' : 'warning'); return loadBookings(); }).catch(function () { setMessage('The queue could not be processed.', 'warning'); }); });\n"
                + "  document.getElementById('run-benchmark').addEventListener('click', loadBenchmark);\n"
                + "  populateTiers(); setMessage('Queue updates appear here.', 'muted'); loadCities(); loadNetwork(); loadBookings();\n"
                + "}());\n";
    }
}
