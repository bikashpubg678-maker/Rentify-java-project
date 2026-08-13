package carrental.service;

import carrental.model.ActivityLog;
import carrental.model.Car;
import carrental.model.Customer;
import carrental.model.Rental;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Backwards-compatible facade over the new focused services.
 *
 * <p>Kept so existing controllers and the mobile API keep compiling while
 * they are migrated one-by-one. New code should depend on the focused services
 * directly: {@link FleetService}, {@link CustomerService}, {@link RentalService},
 * {@link AnalyticsService}, {@link ActivityLogService}.
 *
 * @deprecated prefer the focused services. Will be removed once all callers migrate.
 */
@Service
@Deprecated
public class CarRentalSystem {

    private final FleetService fleet;
    private final CustomerService customers;
    private final RentalService rentals;
    private final AnalyticsService analytics;
    private final ActivityLogService activity;

    public CarRentalSystem(FleetService fleet,
                           CustomerService customers,
                           RentalService rentals,
                           AnalyticsService analytics,
                           ActivityLogService activity) {
        this.fleet = fleet;
        this.customers = customers;
        this.rentals = rentals;
        this.analytics = analytics;
        this.activity = activity;
    }

    // ── Fleet ─────────────────────────────────────────────────────────────
    public List<Car> getAllCars() { return fleet.getAll(); }
    public List<Car> getAvailableCars() { return fleet.getAvailable(); }
    public List<Car> getRentedCars() { return fleet.getRented(); }
    public long totalCars() { return fleet.total(); }
    public long availableCars() { return fleet.availableCount(); }
    public long rentedCars() { return fleet.rentedCount(); }
    public Car addCar(String carId, String brand, String model, double price,
                      String category, String imageUrl) {
        Car car = fleet.add(carId, brand, model, price, category, imageUrl);
        activity.log(ActivityLog.Action.CAR_ADDED,
                "Added " + brand + " " + model + " (" + carId + ") to fleet — $" + price + "/day, " + category);
        return car;
    }
    public void deleteCar(String carId) {
        Car car = fleet.getAll().stream().filter(c -> c.getCarId().equals(carId)).findFirst().orElse(null);
        fleet.delete(carId);
        if (car != null) {
            activity.log(ActivityLog.Action.CAR_DELETED,
                    "Deleted " + car.getBrand() + " " + car.getModel() + " (" + carId + ") from fleet");
        }
    }

    // ── Customers ─────────────────────────────────────────────────────────
    public Customer addCustomer(String name, String phone) {
        return customers.upsertByPhone(name, phone);
    }
    public Customer getCustomerById(Long id) { return customers.getById(id); }
    public List<Customer> searchCustomersByPhone(String phone) { return customers.searchByPhone(phone); }
    public List<Rental> getCustomerRentals(Customer customer) { return customers.rentalsOf(customer.getId()); }
    public Map<String, Object> getCustomerSummary(Customer customer) { return customers.summaryOf(customer); }

    // ── Rentals ───────────────────────────────────────────────────────────
    public Rental rentCar(String carId, String name, String phone,
                          LocalDate startDate, LocalDate endDate, String notes) {
        return rentals.rent(carId, name, phone, startDate, endDate, notes);
    }
    public Rental returnCar(String carId) { return rentals.returnCar(carId); }
    public Rental rateRental(Long rentalId, int rating) { return rentals.rate(rentalId, rating); }
    public List<Rental> getActiveRentals() { return rentals.activeRentals(); }
    public List<Rental> getRentalHistory() { return rentals.history(); }
    public List<Rental> getAllRentals() { return rentals.all(); }
    public double totalRevenue() { return rentals.totalRevenue(); }

    // ── Analytics ─────────────────────────────────────────────────────────
    public Map<String, Double> getAllCarRatings() { return analytics.allCarRatings(); }
    public Map<String, Object> getDashboardInsights() { return analytics.dashboardInsights(); }
    public List<Map<String, Object>> getMonthlyRevenue() { return analytics.monthlyRevenue(); }
    public List<Map<String, Object>> getCategoryBreakdown() { return analytics.categoryBreakdown(); }

    // ── Activity log ──────────────────────────────────────────────────────
    public void logActivity(ActivityLog.Action action, String description) { activity.log(action, description); }
    public List<ActivityLog> getRecentActivity() { return activity.recent(); }
}
