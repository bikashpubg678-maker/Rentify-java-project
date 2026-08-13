package carrental.service;

import carrental.model.Car;
import carrental.model.Customer;
import carrental.model.Rental;
import carrental.repository.RentalRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only analytics: dashboard insights, monthly revenue, category breakdown,
 * car ratings. All methods work from a single repository snapshot.
 */
@Service
public class AnalyticsService {

    private final RentalRepository rentals;

    public AnalyticsService(RentalRepository rentals) {
        this.rentals = rentals;
    }

    public Map<String, Double> allCarRatings() {
        Map<String, List<Integer>> byCar = new HashMap<>();
        for (Rental r : rentals.findByStatus(Rental.Status.RETURNED)) {
            if (r.getRating() != null && r.getCar() != null) {
                byCar.computeIfAbsent(r.getCar().getCarId(), k -> new ArrayList<>())
                     .add(r.getRating());
            }
        }
        Map<String, Double> averages = new HashMap<>();
        for (Map.Entry<String, List<Integer>> e : byCar.entrySet()) {
            double avg = e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            averages.put(e.getKey(), Math.round(avg * 10.0) / 10.0);
        }
        return averages;
    }

    public Map<String, Object> dashboardInsights() {
        List<Rental> all = rentals.findAll();
        Map<String, Object> out = new LinkedHashMap<>();

        Map<Car, Long> carCounts = all.stream()
                .filter(r -> r.getCar() != null)
                .collect(Collectors.groupingBy(Rental::getCar, Collectors.counting()));
        Optional<Map.Entry<Car, Long>> mostRented =
                carCounts.entrySet().stream().max(Map.Entry.comparingByValue());
        if (mostRented.isPresent()) {
            out.put("mostRentedCar", mostRented.get().getKey().getDisplayName());
            out.put("mostRentedCount", mostRented.get().getValue());
        } else {
            out.put("mostRentedCar", "N/A");
            out.put("mostRentedCount", 0L);
        }

        Map<Customer, Long> customerCounts = all.stream()
                .filter(r -> r.getCustomer() != null)
                .collect(Collectors.groupingBy(Rental::getCustomer, Collectors.counting()));
        Optional<Map.Entry<Customer, Long>> top =
                customerCounts.entrySet().stream().max(Map.Entry.comparingByValue());
        if (top.isPresent()) {
            out.put("topCustomer", top.get().getKey().getName());
            out.put("topCustomerRentals", top.get().getValue());
        } else {
            out.put("topCustomer", "N/A");
            out.put("topCustomerRentals", 0L);
        }

        Map<String, Double> monthRevenue = new LinkedHashMap<>();
        for (Rental r : all) {
            if (r.getStartDate() != null) {
                String m = r.getStartDate().getMonth().name().substring(0, 3);
                monthRevenue.merge(m, r.getTotalPrice(), Double::sum);
            }
        }
        Optional<Map.Entry<String, Double>> busiest =
                monthRevenue.entrySet().stream().max(Map.Entry.comparingByValue());
        out.put("busiestMonth", busiest.map(Map.Entry::getKey).orElse("N/A"));
        out.put("busiestMonthRevenue", busiest.map(Map.Entry::getValue).orElse(0.0));

        double avgDays = all.stream().mapToInt(Rental::getDays).average().orElse(0.0);
        out.put("avgRentalDays", Math.round(avgDays * 10.0) / 10.0);

        long unique = all.stream()
                .filter(r -> r.getCustomer() != null)
                .map(r -> r.getCustomer().getId())
                .distinct()
                .count();
        out.put("totalCustomers", unique);
        return out;
    }

    /** Monthly revenue for the current calendar year. */
    public List<Map<String, Object>> monthlyRevenue() {
        int year = LocalDate.now().getYear();
        Map<Month, Double> map = new LinkedHashMap<>();
        for (Month m : Month.values()) map.put(m, 0.0);

        for (Rental r : rentals.findAll()) {
            if (r.getStartDate() != null && r.getStartDate().getYear() == year) {
                map.merge(r.getStartDate().getMonth(), r.getTotalPrice(), Double::sum);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(12);
        for (Map.Entry<Month, Double> e : map.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", e.getKey().name().substring(0, 3));
            row.put("revenue", e.getValue());
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> categoryBreakdown() {
        Map<String, Long> map = rentals.findAll().stream()
                .filter(r -> r.getCar() != null && r.getCar().getCategory() != null)
                .collect(Collectors.groupingBy(r -> r.getCar().getCategory(), Collectors.counting()));
        List<Map<String, Object>> out = new ArrayList<>(map.size());
        for (Map.Entry<String, Long> e : map.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", e.getKey());
            row.put("count", e.getValue());
            out.add(row);
        }
        return out;
    }
}