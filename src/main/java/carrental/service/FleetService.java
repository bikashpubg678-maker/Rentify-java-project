package carrental.service;

import carrental.model.Car;
import carrental.repository.CarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fleet CRUD. Wraps {@link CarRepository} and enforces uniqueness.
 */
@Service
public class FleetService {

    private final CarRepository cars;

    public FleetService(CarRepository cars) {
        this.cars = cars;
    }

    public List<Car> getAll() {
        return cars.findAll();
    }

    public List<Car> getAvailable() {
        return cars.findByAvailableTrue();
    }

    public List<Car> getRented() {
        return cars.findByAvailableFalse();
    }

    public long total() {
        return cars.count();
    }

    public long availableCount() {
        return cars.findByAvailableTrue().size();
    }

    public long rentedCount() {
        return cars.findByAvailableFalse().size();
    }

    @Transactional
    public Car add(String carId, String brand, String model, double price,
                   String category, String imageUrl) {
        String id = carId.toUpperCase().trim();
        if (cars.existsById(id)) {
            throw new IllegalArgumentException("Car ID " + id + " already exists.");
        }
        Car car = new Car(id, brand.trim(), model.trim(), price, category.trim());
        if (imageUrl != null && !imageUrl.isBlank()) {
            car.setImageUrl(imageUrl.trim());
        }
        return cars.save(car);
    }

    @Transactional
    public void delete(String carId) {
        Car car = cars.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        if (!car.isAvailable()) {
            throw new IllegalStateException("Cannot delete a car that is currently rented.");
        }
        cars.deleteById(carId);
    }
}
