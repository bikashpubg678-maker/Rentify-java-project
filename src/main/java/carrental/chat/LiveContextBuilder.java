package carrental.chat;

import carrental.model.Car;
import carrental.model.Customer;
import carrental.model.Rental;
import carrental.repository.CarRepository;
import carrental.repository.CustomerRepository;
import carrental.repository.RentalRepository;
import carrental.service.AnalyticsService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the live Rentify database snapshot that gets prepended to every
 * chat request before it is sent to the LLM. Kept separate from the
 * OpenRouter HTTP code so the prompt logic can be unit-tested.
 */
@Component
public class LiveContextBuilder {

    private final CarRepository cars;
    private final CustomerRepository customers;
    private final RentalRepository rentals;
    private final AnalyticsService analytics;

    public LiveContextBuilder(CarRepository cars,
                              CustomerRepository customers,
                              RentalRepository rentals,
                              AnalyticsService analytics) {
        this.cars = cars;
        this.customers = customers;
        this.rentals = rentals;
        this.analytics = analytics;
    }

    /** Returns the full bilingual-ready snapshot as a plain-text block. */
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("LIVE SYSTEM DATA (authoritative — always use these exact ")
          .append("figures, never estimate or recalculate):\n\n");

        appendRevenueSummary(sb);
        appendFleet(sb);
        appendCategoryBreakdown(sb);
        appendActiveRentals(sb);
        appendRecentHistory(sb);
        appendDashboardInsights(sb);
        appendCarRatings(sb);
        appendLoyaltyTiers(sb);
        appendFeatures(sb);

        return sb.toString();
    }

    // ─── section helpers ──────────────────────────────────────────────────

    private void appendRevenueSummary(StringBuilder sb) {
        java.util.List<Rental> allRentals = rentals.findAll();
        double totalRevenue = allRentals.stream()
                .mapToDouble(Rental::getTotalPrice)
                .sum();
        long active = allRentals.stream()
                .filter(r -> r.getStatus() == Rental.Status.ACTIVE)
                .count();
        long returned = allRentals.stream()
                .filter(r -> r.getStatus() == Rental.Status.RETURNED)
                .count();
        sb.append("REVENUE SUMMARY:\n")
          .append("- Total Revenue (all rentals, all time): $").append(fmt(totalRevenue)).append('\n')
          .append("- Total Rentals Recorded: ").append(allRentals.size()).append('\n')
          .append("- Active Rentals: ").append(active).append('\n')
          .append("- Returned/Completed Rentals: ").append(returned).append("\n\n");
    }

    private void appendFleet(StringBuilder sb) {
        java.util.List<Car> allCars = cars.findAll();
        sb.append("FULL FLEET (").append(allCars.size()).append(" cars total):\n");
        for (Car c : allCars) {
            sb.append("  * [").append(c.getCarId()).append("] ")
              .append(c.getBrand()).append(' ').append(c.getModel())
              .append(" | Category: ").append(c.getCategory())
              .append(" | $").append(fmt(c.getBasePricePerDay())).append("/day")
              .append(" | Status: ").append(c.isAvailable() ? "Available" : "Currently Rented")
              .append('\n');
        }
        sb.append('\n');

        java.util.List<Car> available = allCars.stream().filter(Car::isAvailable).toList();
        sb.append("CURRENTLY AVAILABLE TO RENT (").append(available.size()).append(" cars):\n");
        if (available.isEmpty()) {
            sb.append("  (none — entire fleet is currently rented out)\n");
        } else {
            for (Car c : available) {
                sb.append("  * ").append(c.getBrand()).append(' ').append(c.getModel())
                  .append(" (").append(c.getCategory()).append(") — $")
                  .append(fmt(c.getBasePricePerDay())).append("/day\n");
            }
        }
        sb.append('\n');
    }

    private void appendCategoryBreakdown(StringBuilder sb) {
        Map<String, Long> counts = rentals.findAll().stream()
                .filter(r -> r.getCar() != null && r.getCar().getCategory() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCar().getCategory(), Collectors.counting()));
        sb.append("RENTALS BY CATEGORY:\n");
        if (counts.isEmpty()) {
            sb.append("  (no rentals yet)\n");
        } else {
            counts.forEach((k, v) -> sb.append("  * ").append(k).append(": ")
                    .append(v).append(" rental(s)\n"));
        }
        sb.append('\n');
    }

    private void appendActiveRentals(StringBuilder sb) {
        java.util.List<Rental> active = rentals.findAll().stream()
                .filter(r -> r.getStatus() == Rental.Status.ACTIVE)
                .toList();
        sb.append("ACTIVE RENTALS (currently out, not yet returned):\n");
        if (active.isEmpty()) {
            sb.append("  (none — no cars are currently rented out)\n");
        } else {
            for (Rental r : active) {
                sb.append("  * Rental #").append(r.getId())
                  .append(" | Car: ").append(carLabel(r))
                  .append(" | Customer: ").append(customerLabel(r))
                  .append(" | ").append(r.getStartDateStr()).append(" to ").append(r.getEndDateStr())
                  .append(" | ").append(r.getDays()).append(" day(s)")
                  .append(" | Total: $").append(fmt(r.getTotalPrice())).append('\n');
            }
        }
        sb.append('\n');
    }

    private void appendRecentHistory(StringBuilder sb) {
        java.util.List<Rental> all = rentals.findAll();
        sb.append("FULL RENTAL HISTORY (most recent first, capped at 30):\n");
        if (all.isEmpty()) {
            sb.append("  (no rentals have been recorded yet)\n\n");
            return;
        }
        java.util.List<Rental> sorted = new java.util.ArrayList<>(all);
        sorted.sort(java.util.Comparator.comparing(
                (Rental r) -> r.getStartDate() != null ? r.getStartDate() : java.time.LocalDate.MIN)
                .reversed());
        int shown = 0;
        for (Rental r : sorted) {
            if (shown++ >= 30) {
                sb.append("  …and ").append(sorted.size() - 30).append(" more (omitted to keep prompt small)\n");
                break;
            }
            sb.append("  * Rental #").append(r.getId())
              .append(" | Car: ").append(carLabel(r))
              .append(" | Customer: ").append(customerLabel(r))
              .append(" | ").append(r.getStartDateStr()).append(" to ").append(r.getEndDateStr())
              .append(" | ").append(r.getDays()).append(" day(s)")
              .append(" | Total: $").append(fmt(r.getTotalPrice()))
              .append(" | Status: ").append(r.getStatus())
              .append(" | Notes: ").append((r.getNotes() != null && !r.getNotes().isBlank()) ? r.getNotes() : "None")
              .append('\n');
        }
        sb.append('\n');
    }

    private void appendDashboardInsights(StringBuilder sb) {
        try {
            Map<String, Object> insights = analytics.dashboardInsights();
            sb.append("DASHBOARD INSIGHTS:\n")
              .append("- Most Rented Car: ").append(insights.get("mostRentedCar"))
              .append(" (").append(insights.get("mostRentedCount")).append(" rentals)\n")
              .append("- Top Customer: ").append(insights.get("topCustomer"))
              .append(" (").append(insights.get("topCustomerRentals")).append(" rentals)\n")
              .append("- Busiest Month: ").append(insights.get("busiestMonth"))
              .append(" ($").append(fmt((Double) insights.get("busiestMonthRevenue"))).append(")\n")
              .append("- Average Rental Duration: ").append(insights.get("avgRentalDays")).append(" days\n")
              .append("- Total Unique Customers: ").append(insights.get("totalCustomers")).append("\n\n");
        } catch (Exception ignored) {
            // Insights are best-effort — never break the chat on this.
        }
    }

    private void appendCarRatings(StringBuilder sb) {
        try {
            Map<String, Double> ratings = analytics.allCarRatings();
            sb.append("CAR RATINGS:\n");
            if (ratings.isEmpty()) {
                sb.append("  (no ratings yet)\n");
            } else {
                ratings.forEach((id, r) ->
                        sb.append("  * Car ").append(id).append(": ")
                          .append(String.format(Locale.ROOT, "%.1f", r))
                          .append(" / 5.0 stars\n"));
            }
            sb.append('\n');
        } catch (Exception ignored) {
            // skip
        }
    }

    private void appendFeatures(StringBuilder sb) {
        sb.append("AVAILABLE FEATURES YOU CAN HELP WITH:\n")
          .append("- Dashboard: View fleet stats, revenue, and insights\n")
          .append("- Rent a Car: Date-based booking with live price preview and double-booking prevention\n")
          .append("- Return a Car: One-click return of any active rental\n")
          .append("- Rental History: Full record of active and completed rentals\n")
          .append("- PDF Receipts: Download professional PDF receipt for any booking\n")
          .append("- Revenue Charts: Monthly revenue bar chart + category doughnut chart\n")
          .append("- Manage Fleet: Add new cars or delete existing ones from the UI\n")
          .append("- Customer Ratings: Rate returned rentals from 1-5 stars; view average rating per car\n")
          .append("- Dashboard Insights: See most rented car, top customer, busiest month, and avg rental duration\n")
          .append("- CSV Export: Download complete rental history as a CSV file\n")
          .append("- Booking Notes: Customers can add special requests (e.g. baby seat, preferred color) when renting\n")
          .append("- Customer Profiles: Search customers by phone number and view their complete rental history, stats, and ratings\n")
          .append("- Activity Log: Track all actions performed in the system — car added, deleted, rented, returned with timestamps\n")
          .append("- Loyalty Discounts: Returning customers get 5% off after 3 rentals, 10% off after 5, 15% off after 10\n")
          .append("- Rental Agreement: View printable rental agreement with full terms and conditions\n")
          .append("- About Page: Developer info for Bikash Talukder — LinkedIn and GitHub profiles\n")
          .append("- Theme Toggle: Switch between dark and light mode from the navigation bar\n")
          .append("- AI Assistant (English + Bengali): Ask questions about any of the above features\n");
    }

    private void appendLoyaltyTiers(StringBuilder sb) {
        try {
            Map<String, Integer> counts = new HashMap<>();
            for (Rental r : rentals.findAll()) {
                if (r.getCustomer() == null) continue;
                String name = r.getCustomer().getName();
                counts.merge(name != null ? name : "Unknown", 1, Integer::sum);
            }
            sb.append("\nCUSTOMER LOYALTY TIERS:\n");
            if (counts.isEmpty()) {
                sb.append("  (no customers yet)\n");
                return;
            }
            counts.forEach((name, count) -> {
                String discount = count >= 10 ? "15%" : count >= 5 ? "10%" : count >= 3 ? "5%" : "0%";
                sb.append("  * ").append(name).append(": ").append(count)
                  .append(" rental(s) — Loyalty Discount: ").append(discount).append('\n');
            });
        } catch (Exception ignored) {
            // skip
        }
    }

    // ─── misc helpers ─────────────────────────────────────────────────────

    private static String carLabel(Rental r) {
        return r.getCar() != null ? r.getCar().getBrand() + " " + r.getCar().getModel() : "Unknown car";
    }

    private static String customerLabel(Rental r) {
        if (r.getCustomer() == null) return "Unknown customer";
        String name = r.getCustomer().getName();
        return (name != null && !name.isBlank()) ? name : "Customer";
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Reported so the controller can mention the day in the chat response. */
    public LocalDate today() {
        return LocalDate.now();
    }

    /** Touched so callers don't get warnings on unused field. */
    @SuppressWarnings("unused")
    private long totalCustomers() {
        return customers.count();
    }
}