package carrental.service;

import carrental.model.Customer;
import carrental.model.Rental;
import carrental.repository.CustomerRepository;
import carrental.repository.RentalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Customer CRUD, lookup, and per-customer analytics.
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final RentalRepository rentals;

    public CustomerService(CustomerRepository customers, RentalRepository rentals) {
        this.customers = customers;
        this.rentals = rentals;
    }

    @Transactional
    public Customer upsertByPhone(String name, String phone) {
        String p = phone.trim();
        List<Customer> existing = customers.findByPhoneContaining(p);
        for (Customer c : existing) {
            if (c.getPhone().equals(p)) return c;
        }
        long count = customers.count();
        String id = String.format("CUS%03d", count + 1);
        return customers.save(new Customer(id, name.trim(), p));
    }

    public Customer getById(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    public List<Customer> searchByPhone(String phone) {
        if (phone == null || phone.isBlank()) return List.of();
        return customers.findByPhoneContaining(phone.trim());
    }

    public List<Rental> rentalsOf(Long customerId) {
        return rentals.findAll().stream()
                .filter(r -> r.getCustomer() != null && customerId.equals(r.getCustomer().getId()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> summaryOf(Customer customer) {
        List<Rental> rentals = rentalsOf(customer.getId());
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalRentals", rentals.size());
        s.put("activeRentals", rentals.stream().filter(r -> r.getStatus() == Rental.Status.ACTIVE).count());
        s.put("returnedRentals", rentals.stream().filter(r -> r.getStatus() == Rental.Status.RETURNED).count());
        s.put("totalSpent", rentals.stream().mapToDouble(Rental::getTotalPrice).sum());
        s.put("avgRating", rentals.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(Rental::getRating)
                .average()
                .orElse(0.0));
        return s;
    }
}