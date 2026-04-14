package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

public class AirlineSystem {
    private final FlightGraph network;
    private final BookingQueue bookingQueue;

    public AirlineSystem() {
        this.network = buildDefaultNetwork();
        this.bookingQueue = new BookingQueue();
        seedBookings();
    }

    public FlightGraph getNetwork() {
        return network;
    }

    public synchronized List<String> getCities() {
        return new ArrayList<String>(network.getCities());
    }

    public synchronized RouteResult findRoute(String source, String target, boolean byCost) {
        return dijkstra(network, source, target, byCost);
    }

    public synchronized void addBooking(String passengerName, int loyaltyTier, String from, String to) {
        bookingQueue.addBooking(new Booking(passengerName, loyaltyTier, from, to));
    }

    public synchronized Booking processNextBooking() {
        return bookingQueue.processNext();
    }

    public synchronized List<Booking> getQueuedBookings() {
        return bookingQueue.snapshot();
    }

    public synchronized int getQueueSize() {
        return bookingQueue.size();
    }

    public synchronized List<BenchmarkResult> runPerformanceBenchmark() {
        int[] sizes = {10, 50, 100, 250, 500, 1000};
        List<BenchmarkResult> results = new ArrayList<BenchmarkResult>();
        Random rand = new Random(42);

        for (int n : sizes) {
            FlightGraph graph = new FlightGraph();
            for (int i = 0; i < n; i++) {
                graph.addCity("City_" + i);
            }

            int edgeCount = 0;
            String[] cities = graph.getCities().toArray(new String[0]);
            for (int i = 0; i < n; i++) {
                int connections = Math.min(5, n - 1);
                Set<Integer> seen = new HashSet<Integer>();
                for (int c = 0; c < connections; c++) {
                    int j = rand.nextInt(n);
                    if (j != i && seen.add(j)) {
                        graph.addFlight(cities[i], cities[j],
                                100 + rand.nextInt(900),
                                30 + rand.nextInt(300));
                        edgeCount++;
                    }
                }
            }

            long start = System.nanoTime();
            dijkstra(graph, cities[0], cities[n - 1], true);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            results.add(new BenchmarkResult(n, edgeCount, elapsedMs));
        }

        return results;
    }

    public synchronized String describeNetwork() {
        StringBuilder builder = new StringBuilder();
        for (String city : network.getCities()) {
            builder.append(city).append(System.lineSeparator());
            for (Flight flight : network.getFlights(city)) {
                builder.append("  -> ")
                        .append(String.format("%-15s", flight.destination))
                        .append(" $")
                        .append(flight.cost)
                        .append(" | ")
                        .append(flight.duration)
                        .append(" min")
                        .append(System.lineSeparator());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private void seedBookings() {
        addBooking("Alice Chen", 0, "Toronto", "Vancouver");
        addBooking("Bob Martinez", 2, "Montreal", "Paris");
        addBooking("Carol Williams", 1, "New York", "London");
        addBooking("David Kim", 3, "Toronto", "Tokyo");
        addBooking("Emma Patel", 2, "Vancouver", "Sydney");
        addBooking("Frank Thompson", 0, "Chicago", "Dallas");
        addBooking("Grace Liu", 3, "Toronto", "Dubai");
        addBooking("Henry Brown", 1, "Miami", "Bogota");
    }

    private static FlightGraph buildDefaultNetwork() {
        FlightGraph graph = new FlightGraph();

        graph.addRoute("Toronto", "Montreal", 220, 95);
        graph.addRoute("Toronto", "Montreal", 145, 125);
        graph.addRoute("Toronto", "Vancouver", 480, 295);
        graph.addRoute("Toronto", "Vancouver", 340, 355);
        graph.addRoute("Toronto", "Chicago", 310, 100);
        graph.addRoute("Toronto", "Chicago", 180, 150);
        graph.addRoute("Toronto", "New York", 180, 75);
        graph.addRoute("Toronto", "New York", 120, 105);
        graph.addRoute("Montreal", "New York", 150, 80);
        graph.addRoute("Montreal", "Paris", 750, 420);
        graph.addRoute("Montreal", "Paris", 540, 505);
        graph.addRoute("New York", "London", 680, 415);
        graph.addRoute("New York", "London", 260, 510);
        graph.addRoute("New York", "Miami", 290, 180);
        graph.addRoute("New York", "Miami", 180, 240);
        graph.addRoute("Chicago", "Los Angeles", 390, 250);
        graph.addRoute("Chicago", "Los Angeles", 240, 325);
        graph.addRoute("Chicago", "Dallas", 270, 150);
        graph.addRoute("Chicago", "Dallas", 170, 215);
        graph.addRoute("Vancouver", "Los Angeles", 210, 150);
        graph.addRoute("Vancouver", "Los Angeles", 140, 210);
        graph.addRoute("Los Angeles", "Tokyo", 1100, 620);
        graph.addRoute("Los Angeles", "Tokyo", 760, 760);
        graph.addRoute("Los Angeles", "Sydney", 1400, 850);
        graph.addRoute("Los Angeles", "Sydney", 980, 980);
        graph.addRoute("London", "Paris", 160, 75);
        graph.addRoute("London", "Paris", 70, 105);
        graph.addRoute("London", "Dubai", 650, 390);
        graph.addRoute("London", "Dubai", 330, 500);
        graph.addRoute("Dubai", "Tokyo", 700, 420);
        graph.addRoute("Dubai", "Tokyo", 310, 535);
        graph.addRoute("Tokyo", "Sydney", 800, 480);
        graph.addRoute("Tokyo", "Sydney", 340, 610);
        graph.addRoute("Paris", "Dubai", 550, 380);
        graph.addRoute("Paris", "Dubai", 210, 470);
        graph.addRoute("Miami", "Bogota", 420, 280);
        graph.addRoute("Miami", "Bogota", 250, 360);
        graph.addRoute("Dallas", "Miami", 240, 160);
        graph.addRoute("Dallas", "Miami", 150, 225);

        return graph;
    }

    public static RouteResult dijkstra(FlightGraph graph, String source, String target, boolean byCost) {
        if (!graph.getCities().contains(source) || !graph.getCities().contains(target)) {
            return RouteResult.notFound();
        }

        Map<String, Integer> dist = new HashMap<String, Integer>();
        Map<String, String> prev = new HashMap<String, String>();
        Map<String, Integer> otherDim = new HashMap<String, Integer>();
        Map<String, Integer> hops = new HashMap<String, Integer>();

        for (String city : graph.getCities()) {
            dist.put(city, Integer.MAX_VALUE);
            otherDim.put(city, Integer.MAX_VALUE);
            hops.put(city, Integer.MAX_VALUE);
        }

        dist.put(source, 0);
        otherDim.put(source, 0);
        hops.put(source, 0);

        PriorityQueue<DijkstraNode> queue = new PriorityQueue<DijkstraNode>();
        queue.offer(new DijkstraNode(source, 0));

        while (!queue.isEmpty()) {
            DijkstraNode current = queue.poll();
            if (current.distance > dist.get(current.city)) {
                continue;
            }

            for (Flight flight : graph.getFlights(current.city)) {
                int weight = byCost ? flight.cost : flight.duration;
                int secondaryWeight = byCost ? flight.duration : flight.cost;
                int candidate = dist.get(current.city) + weight;
                int candidateOther = otherDim.get(current.city) + secondaryWeight;
                int candidateHops = hops.get(current.city) + 1;

                if ((candidate < dist.get(flight.destination))
                        || (candidate == dist.get(flight.destination) && candidateOther < otherDim.get(flight.destination))
                        || (candidate == dist.get(flight.destination)
                        && candidateOther == otherDim.get(flight.destination)
                        && candidateHops < hops.get(flight.destination))) {
                    dist.put(flight.destination, candidate);
                    otherDim.put(flight.destination, candidateOther);
                    hops.put(flight.destination, candidateHops);
                    prev.put(flight.destination, current.city);
                    queue.offer(new DijkstraNode(flight.destination, candidate));
                }
            }
        }

        if (dist.get(target) == Integer.MAX_VALUE) {
            return RouteResult.notFound();
        }

        LinkedList<String> path = new LinkedList<String>();
        for (String at = target; at != null; at = prev.get(at)) {
            path.addFirst(at);
        }

        int totalCost = byCost ? dist.get(target) : otherDim.get(target);
        int totalDuration = byCost ? otherDim.get(target) : dist.get(target);
        return new RouteResult(path, totalCost, totalDuration, true);
    }

    public static class Flight {
        private final String destination;
        private final int cost;
        private final int duration;

        public Flight(String destination, int cost, int duration) {
            this.destination = destination;
            this.cost = cost;
            this.duration = duration;
        }

        public String getDestination() {
            return destination;
        }

        public int getCost() {
            return cost;
        }

        public int getDuration() {
            return duration;
        }
    }

    public static class Booking implements Comparable<Booking> {
        public static final String[] TIER_NAMES = {"Regular", "Silver", "Gold", "Platinum"};

        private final String passengerName;
        private final int loyaltyTier;
        private final String from;
        private final String to;
        private final long timestamp;

        public Booking(String passengerName, int loyaltyTier, String from, String to) {
            this.passengerName = passengerName;
            this.loyaltyTier = loyaltyTier;
            this.from = from;
            this.to = to;
            this.timestamp = System.nanoTime();
        }

        public String getPassengerName() {
            return passengerName;
        }

        public int getLoyaltyTier() {
            return loyaltyTier;
        }

        public String getTierName() {
            return TIER_NAMES[loyaltyTier];
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        @Override
        public int compareTo(Booking other) {
            if (loyaltyTier != other.loyaltyTier) {
                return Integer.valueOf(other.loyaltyTier).compareTo(loyaltyTier);
            }
            return Long.valueOf(timestamp).compareTo(other.timestamp);
        }
    }

    public static class RouteResult {
        private final List<String> path;
        private final int totalCost;
        private final int totalDuration;
        private final boolean found;

        public RouteResult(List<String> path, int totalCost, int totalDuration, boolean found) {
            this.path = new ArrayList<String>(path);
            this.totalCost = totalCost;
            this.totalDuration = totalDuration;
            this.found = found;
        }

        public static RouteResult notFound() {
            return new RouteResult(Collections.<String>emptyList(), 0, 0, false);
        }

        public List<String> getPath() {
            return new ArrayList<String>(path);
        }

        public int getTotalCost() {
            return totalCost;
        }

        public int getTotalDuration() {
            return totalDuration;
        }

        public boolean isFound() {
            return found;
        }

        public String formatForDisplay() {
            if (!found) {
                return "No route is available for the selected cities.";
            }

            StringBuilder builder = new StringBuilder();
            builder.append("Route: ")
                    .append(joinPath(path))
                    .append(System.lineSeparator());
            builder.append("Total cost: $").append(totalCost).append(System.lineSeparator());
            builder.append("Total time: ")
                    .append(totalDuration)
                    .append(" min (")
                    .append(totalDuration / 60)
                    .append("h ")
                    .append(totalDuration % 60)
                    .append("m)");
            return builder.toString();
        }

        private String joinPath(List<String> cities) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < cities.size(); i++) {
                if (i > 0) {
                    builder.append(" -> ");
                }
                builder.append(cities.get(i));
            }
            return builder.toString();
        }
    }

    public static class BenchmarkResult {
        private final int cityCount;
        private final int edgeCount;
        private final long elapsedMs;

        public BenchmarkResult(int cityCount, int edgeCount, long elapsedMs) {
            this.cityCount = cityCount;
            this.edgeCount = edgeCount;
            this.elapsedMs = elapsedMs;
        }

        public int getCityCount() {
            return cityCount;
        }

        public int getEdgeCount() {
            return edgeCount;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }

    public static class FlightGraph {
        private final Map<String, List<Flight>> adjacencyList = new LinkedHashMap<String, List<Flight>>();

        public void addCity(String city) {
            if (!adjacencyList.containsKey(city)) {
                adjacencyList.put(city, new ArrayList<Flight>());
            }
        }

        public void addFlight(String from, String to, int cost, int duration) {
            addCity(from);
            addCity(to);
            adjacencyList.get(from).add(new Flight(to, cost, duration));
        }

        public void addRoute(String a, String b, int cost, int duration) {
            addFlight(a, b, cost, duration);
            addFlight(b, a, cost, duration);
        }

        public Set<String> getCities() {
            return adjacencyList.keySet();
        }

        public List<Flight> getFlights(String city) {
            List<Flight> flights = adjacencyList.get(city);
            if (flights == null) {
                return Collections.emptyList();
            }
            return flights;
        }
    }

    private static class DijkstraNode implements Comparable<DijkstraNode> {
        private final String city;
        private final int distance;

        private DijkstraNode(String city, int distance) {
            this.city = city;
            this.distance = distance;
        }

        @Override
        public int compareTo(DijkstraNode other) {
            return Integer.valueOf(distance).compareTo(other.distance);
        }
    }

    private static class BookingQueue {
        private final PriorityQueue<Booking> queue = new PriorityQueue<Booking>();

        public void addBooking(Booking booking) {
            queue.offer(booking);
        }

        public Booking processNext() {
            return queue.poll();
        }

        public int size() {
            return queue.size();
        }

        public List<Booking> snapshot() {
            List<Booking> bookings = new ArrayList<Booking>(queue);
            Collections.sort(bookings);
            return bookings;
        }
    }
}
