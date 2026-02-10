package ru.rollsroms.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Catalog {
  private final List<Product> items;

  private Catalog(List<Product> items) {
    this.items = items;
  }

  public static Catalog defaultCatalog() {
    List<Product> items = new ArrayList<>();
    items.add(new Product(
        "classic",
        "Ромовые шарики классические",
        "Классический, с шоколадной вермишелью",
        43000,
        true,
        "images/1.jpg",
        "images/1s.jpg"
    ));
    items.add(new Product(
        "pine",
        "Ромовые шарики с кедровым орехом",
        "Кедровый орех, ароматный ромовый бисквит",
        43000,
        false,
        "images/2.jpg",
        "images/2.jpg"
    ));
    items.add(new Product(
        "walnut",
        "Ромовые шарики с обсыпкой грецким орехом",
        "Грецкий орех, нежный ромовый крем",
        43000,
        false,
        "images/3.jpg",
        "images/3.jpg"
    ));
    return new Catalog(items);
  }

  public int size() {
    return items.size();
  }

  public Product get(int index) {
    if (items.isEmpty()) {
      throw new IllegalStateException("Catalog is empty");
    }
    int safeIndex = Math.floorMod(index, items.size());
    return items.get(safeIndex);
  }

  public Optional<Product> findById(String id) {
    return items.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  public List<Product> all() {
    return List.copyOf(items);
  }
}
