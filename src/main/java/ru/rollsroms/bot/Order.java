package ru.rollsroms.bot;

import java.util.List;

public record Order(long userId, String tag, String phone, String address,
                    List<OrderItem> items, int total, String currency, String payload) {
}
