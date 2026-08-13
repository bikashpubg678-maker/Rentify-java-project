package carrental.service;

import carrental.model.Customer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    private static Customer customerWithRentals(int n) {
        Customer c = new Customer("id-" + n, "Name " + n, "01" + n);
        // incrementRentalCount is the only API that recomputes loyaltyDiscount.
        for (int i = 0; i < n; i++) c.incrementRentalCount();
        return c;
    }

    @Test
    void firstTimeCustomerGetsNoDiscount() {
        Customer c = customerWithRentals(0);
        assertEquals(100.0, pricing.applyLoyaltyDiscount(c, 100.0), 1e-9);
    }

    @Test
    void fivePercentAfterThreeRentals() {
        Customer c = customerWithRentals(3);
        assertEquals(95.0, pricing.applyLoyaltyDiscount(c, 100.0), 1e-9);
    }

    @Test
    void tenPercentAfterFiveRentals() {
        Customer c = customerWithRentals(5);
        assertEquals(90.0, pricing.applyLoyaltyDiscount(c, 100.0), 1e-9);
    }

    @Test
    void fifteenPercentAfterTenRentals() {
        Customer c = customerWithRentals(10);
        assertEquals(85.0, pricing.applyLoyaltyDiscount(c, 100.0), 1e-9);
    }

    @Test
    void discountIsMonotonicallyNonIncreasing() {
        double prev = 100.0;
        for (int n = 0; n <= 12; n++) {
            double now = pricing.applyLoyaltyDiscount(customerWithRentals(n), 100.0);
            assertTrue(now <= prev + 1e-9, "discount should never grow with more rentals");
            prev = now;
        }
    }
}