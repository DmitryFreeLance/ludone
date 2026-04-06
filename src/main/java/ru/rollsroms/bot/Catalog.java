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
        "Ромовый шарик «Rolls-Roms» классический",
        "классический",
        true,
        "images/1.jpg",
        "images/1s.jpg",
        49500,
        39500,
        37500,
        36000
    ));
    items.add(new Product(
        "pine",
        "Ромовый шарик «Rolls-Roms» с кедровым орехом",
        "с кедровым орехом",
        true,
        "images/2.jpg",
        "images/2s.jpg",
        57000,
        48500,
        45500,
        43000
    ));
    items.add(new Product(
        "walnut",
        "Ромовый шарик «Rolls-Roms» с грецким орехом",
        "с грецким орехом",
        true,
        "images/3.jpg",
        "images/3s.jpg",
        51500,
        43500,
        41000,
        38500
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
