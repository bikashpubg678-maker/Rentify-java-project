package carrental.service;

import carrental.model.ActivityLog;
import carrental.model.Car;
import carrental.model.Customer;
import carrental.model.Rental;
import carrental.repository.CarRepository;
import carrental.repository.RentalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Rental lifecycle: book, return, rate. Double-booking prevention.
 */
@Service
public class RentalService {

    private final RentalRepository rentals;
    private final CarRepository cars;
    private final CustomerService customers;
    private final PricingService pricing;
    private final ActivityLogService activity;

    public RentalService(RentalRepository rentals,
                         CarRepository cars,
                         CustomerService customers,
                         PricingService pricing,
                         ActivityLogService activity) {
        this.rentals = rentals;
        this.cars = cars;
        this.customers = customers;
        this.pricing = pricing;
        this.activity = activity;
    }

    @Transactional
    public Rental rent(String carId, String name, String phone,
                       LocalDate startDate, LocalDate endDate, String notes) {
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("End date must be after start date.");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Start date cannot be in the past.");
        }

        Car car = cars.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        if (!car.isAvailable()) {
            throw new IllegalStateException("Car " + carId + " is not available.");
        }

        List<Rental> overlapping = rentals.findOverlapping(car, startDate, endDate);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("Car is already booked for those dates.");
        }

        Customer customer = customers.upsertByPhone(name, phone);
        car.rent();
        cars.save(car);

        Rental rental = new Rental(car, customer, startDate, endDate);
        double discounted = pricing.applyLoyaltyDiscount(customer, rental.getTotalPrice());
        if (discounted < rental.getTotalPrice()) {
            rental.setTotalPrice(discounted);
        }
        if (notes != null && !notes.isBlank()) {
            rental.setNotes(notes.trim());
        }
        rental = rentals.save(rental);

        customer.incrementRentalCount();

        activity.log(ActivityLog.Action.CAR_RENTED,
                "Rented " + car.getBrand() + " " + car.getModel() + " (" + carId + ") to " + name
                + " | " + startDate + " to " + endDate + " | $" + String.format("%.2f", rental.getTotalPrice())
                + (customer.getLoyaltyDiscount() > 0 ? " (" + (int)(customer.getLoyaltyDiscount()*100) + "% loyalty discount applied)" : ""));

        return rental;
    }

    @Transactional
    public Rental returnCar(String carId) {
        Car car = cars.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        Rental rental = rentals.findByCar_CarIdAndStatus(carId, Rental.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active rental for car " + carId));
        car.returnCar();
        cars.save(car);
        rental.markReturned();
        rental = rentals.save(rental);

        activity.log(ActivityLog.Action.CAR_RETURNED,
                "Returned " + car.getBrand() + " " + car.getModel() + " (" + carId + ") from "
                + rental.getCustomer().getName()
                + " | Duration: " + rental.getDays() + " day(s) | Total: $" + String.format("%.2f", rental.getTotalPrice()));

        return rental;
    }

    @Transactional
    public Rental rate(Long rentalId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        Rental rental = rentals.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
        if (rental.getStatus() != Rental.Status.RETURNED) {
            throw new IllegalStateException("Can only rate returned rentals");
        }
        rental.setRating(rating);
        return rentals.save(rental);
    }

    public Rental findById(Long id) {
        return rentals.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
    }

    public List<Rental> activeRentals() {
        return rentals.findByStatus(Rental.Status.ACTIVE);
    }

    public List<Rental> history() {
        return rentals.findByStatus(Rental.Status.RETURNED);
    }

    public List<Rental> all() {
        return rentals.findAll();
    }

    public double totalRevenue() {
        return rentals.findAll().stream().mapToDouble(Rental::getTotalPrice).sum();
    }
}