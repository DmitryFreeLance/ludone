package ru.rollsroms.bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReceiptBuilder {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ReceiptBuilder() {
  }

  public static String build(Order order, int taxSystemCode) {
    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, Object> receipt = new LinkedHashMap<>();

    Map<String, Object> customer = new LinkedHashMap<>();
    customer.put("phone", order.phone());
    receipt.put("customer", customer);
    receipt.put("tax_system_code", taxSystemCode);

    List<Map<String, Object>> items = new ArrayList<>();
    for (OrderItem item : order.items()) {
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("description", item.product().title());
      line.put("quantity", formatQty(item.quantity()));
      line.put("amount", amount(item.unitPrice() * item.quantity(), order.currency()));
      line.put("vat_code", 1);
      line.put("payment_subject", "commodity");
      line.put("payment_mode", "full_payment");
      items.add(line);
    }
    receipt.put("items", items);
    root.put("receipt", receipt);

    try {
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to build receipt", e);
    }
  }

  private static Map<String, Object> amount(int total, String currency) {
    Map<String, Object> amount = new LinkedHashMap<>();
    amount.put("value", formatMoney(total));
    amount.put("currency", currency);
    return amount;
  }

  private static String formatMoney(int kopeks) {
    return String.format(Locale.US, "%.2f", kopeks / 100.0);
  }

  private static String formatQty(int qty) {
    return String.format(Locale.US, "%.2f", (double) qty);
  }
}
