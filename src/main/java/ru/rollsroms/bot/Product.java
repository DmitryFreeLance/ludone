package ru.rollsroms.bot;

import java.util.Locale;

public record Product(
    String id,
    String title,
    String description,
    boolean available,
    String image,
    int priceFor1,
    int priceFor2,
    int priceFor3to5,
    int priceFor6Plus
) {
  public int unitPriceFor(int qty) {
    if (qty <= 1) {
      return priceFor1;
    }
    if (qty == 2) {
      return priceFor2;
    }
    if (qty <= 5) {
      return priceFor3to5;
    }
    return priceFor6Plus;
  }

  public String priceTiersText() {
    return String.format(
        Locale.US,
        "<i>Цена за 1 шт:\n1 шт — %d₽/шт\n2 шт — %d₽/шт\n3-5 шт — %d₽/шт\n6+ шт — %d₽/шт</i>",
        priceFor1 / 100,
        priceFor2 / 100,
        priceFor3to5 / 100,
        priceFor6Plus / 100
    );
  }
}
