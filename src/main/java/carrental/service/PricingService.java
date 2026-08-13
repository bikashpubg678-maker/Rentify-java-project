package carrental.service;

import carrental.model.Customer;
import org.springframework.stereotype.Service;

/**
 * Pure pricing logic: loyalty discount tiers.
 *
 * <p>Extracted so it can be unit-tested without spinning up the DB.
 */
@Service
public class PricingService {

    public double applyLoyaltyDiscount(Customer customer, double basePrice) {
        double pct = customer.getLoyaltyDiscount();
        if (pct <= 0) return basePrice;
        return basePrice * (1 - pct);
    }
}
