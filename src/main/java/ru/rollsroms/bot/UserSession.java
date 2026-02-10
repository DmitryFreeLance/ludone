package ru.rollsroms.bot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

public final class UserSession {
  public enum State {
    IDLE,
    VIEWING_CATALOG,
    AWAITING_QTY,
    AWAITING_PHONE,
    AWAITING_ADDRESS,
    WAITING_PAYMENT,
    ADMIN_ADD
  }

  public State state = State.IDLE;
  public int catalogIndex = 0;
  public Integer catalogMessageId = null;
  public final List<Integer> botMessages = new ArrayList<>();
  public final Set<String> cart = new LinkedHashSet<>();
  public final Map<String, Integer> quantities = new LinkedHashMap<>();
  public int qtyIndex = 0;
  public String phone = null;
  public String address = null;
  public String userTag = null;
  public boolean invoiceSent = false;
  public ScheduledFuture<?> invoiceTask = null;
  public Order pendingOrder = null;

  public void resetOrder() {
    cart.clear();
    quantities.clear();
    qtyIndex = 0;
    phone = null;
    address = null;
    invoiceSent = false;
    pendingOrder = null;
    cancelInvoiceTask();
  }

  public void cancelInvoiceTask() {
    if (invoiceTask != null) {
      invoiceTask.cancel(false);
      invoiceTask = null;
    }
  }
}
