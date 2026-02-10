package ru.rollsroms.bot;

import java.util.Locale;

public record Product(String id, String title, String description, int price, String image, String smallImage) {
  public String priceLabel(String currency) {
    return String.format(Locale.US, "%.2f %s", price / 100.0, currency);
  }
}
