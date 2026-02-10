package ru.rollsroms.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public final class Main {
  public static void main(String[] args) throws Exception {
    BotConfig config = BotConfig.fromEnv();
    Database db = new Database(config.dbPath());
    db.init();
    db.ensureAdmins(config.adminIds());

    MediaStore mediaStore = new MediaStore();
    Catalog catalog = Catalog.defaultCatalog();

    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
    botsApi.registerBot(new RollsRomsBot(config, db, catalog, mediaStore));

    System.out.println("Rolls Roms bot started");
  }
}
