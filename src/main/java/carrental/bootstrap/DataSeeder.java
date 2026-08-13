package carrental.bootstrap;

import carrental.model.Car;
import carrental.repository.CarRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the fleet with a small set of demo cars on first startup.
 * Idempotent — skipped if any cars already exist.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final CarRepository cars;

    public DataSeeder(CarRepository cars) {
        this.cars = cars;
    }

    @Override
    public void run(String... args) {
        if (cars.count() > 0) return;

        // Sedans
        cars.save(new Car("C001", "Toyota", "Camry", 60.0, "Sedan"));
        cars.save(new Car("C002", "Honda", "Accord", 70.0, "Sedan"));
        cars.save(new Car("C012", "Honda", "Civic", 55.0, "Sedan"));
        cars.save(new Car("C017", "Toyota", "Corolla", 50.0, "Sedan"));
        // SUVs
        cars.save(new Car("C003", "Mahindra", "Thar", 150.0, "SUV"));
        cars.save(new Car("C006", "Hyundai", "Creta", 80.0, "SUV"));
        cars.save(new Car("C009", "Audi", "Q7", 190.0, "SUV"));
        cars.save(new Car("C011", "Toyota", "Fortuner", 130.0, "SUV"));
        cars.save(new Car("C016", "Kia", "Seltos", 75.0, "SUV"));
        // Sports
        cars.save(new Car("C004", "Ford", "Mustang", 200.0, "Sports"));
        cars.save(new Car("C010", "Porsche", "911", 350.0, "Sports"));
        cars.save(new Car("C015", "Chevrolet", "Camaro", 180.0, "Sports"));
        cars.save(new Car("C018", "BMW", "M4", 280.0, "Sports"));
        // Luxury
        cars.save(new Car("C005", "BMW", "X5", 250.0, "Luxury"));
        cars.save(new Car("C008", "Mercedes-Benz", "E-Class", 220.0, "Luxury"));
        cars.save(new Car("C014", "Range Rover", "Velar", 300.0, "Luxury"));
        // Electric
        cars.save(new Car("C007", "Tesla", "Model 3", 180.0, "Electric"));
        // Hatchback
        cars.save(new Car("C013", "Maruti Suzuki", "Swift", 40.0, "Hatchback"));
    }
}