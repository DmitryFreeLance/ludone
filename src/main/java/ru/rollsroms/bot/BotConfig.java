package ru.rollsroms.bot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BotConfig {
  private final String token;
  private final String username;
  private final String providerToken;
  private final String currency;
  private final String dbPath;
  private final Set<Long> adminIds;
  private final int taxSystemCode;

  private BotConfig(String token, String username, String providerToken, String currency, String dbPath,
                    Set<Long> adminIds, int taxSystemCode) {
    this.token = token;
    this.username = username;
    this.providerToken = providerToken;
    this.currency = currency;
    this.dbPath = dbPath;
    this.adminIds = adminIds;
    this.taxSystemCode = taxSystemCode;
  }

  public static BotConfig fromEnv() {
    String token = requireEnv("BOT_TOKEN");
    String username = requireEnv("BOT_USERNAME");
    String providerToken = requireEnv("PAYMENT_PROVIDER_TOKEN");
    String currency = env("PAYMENT_CURRENCY", "RUB");
    String dbPath = env("DB_PATH", "./data/bot.db");
    int taxSystemCode = Integer.parseInt(env("TAX_SYSTEM_CODE", "1"));
    Set<Long> adminIds = parseAdminIds(env("BOT_ADMIN_IDS", ""));
    return new BotConfig(token, username, providerToken, currency, dbPath, adminIds, taxSystemCode);
  }

  public String token() {
    return token;
  }

  public String username() {
    return username;
  }

  public String providerToken() {
    return providerToken;
  }

  public String currency() {
    return currency;
  }

  public String dbPath() {
    return dbPath;
  }

  public Set<Long> adminIds() {
    return Collections.unmodifiableSet(adminIds);
  }

  public int taxSystemCode() {
    return taxSystemCode;
  }

  private static String env(String key, String fallback) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String requireEnv(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required env: " + key);
    }
    return value.trim();
  }

  private static Set<Long> parseAdminIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return new HashSet<>();
    }
    Set<Long> ids = new HashSet<>();
    for (String part : raw.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        ids.add(Long.parseLong(trimmed));
      }
    }
    return ids;
  }
}
