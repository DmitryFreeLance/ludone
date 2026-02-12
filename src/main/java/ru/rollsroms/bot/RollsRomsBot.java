package ru.rollsroms.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class RollsRomsBot extends TelegramLongPollingBot {
  private static final String CB_START_YES = "start_yes";
  private static final String CB_START_NO = "start_no";
  private static final String CB_CATALOG_PREV = "cat_prev";
  private static final String CB_CATALOG_NEXT = "cat_next";
  private static final String CB_CATALOG_ADD_PREFIX = "add:";
  private static final String CB_CATALOG_CHECKOUT = "checkout";
  private static final String CB_PAY_NOW = "pay_now";
  private static final String CB_BACK_START = "back_start";
  private static final String CB_ORDER_AGAIN = "order_again";
  private static final String CB_ADMIN_ADD = "admin_add";
  private static final String CB_ADMIN_LIST = "admin_list";
  private static final String CB_NOOP = "noop";

  private final BotConfig config;
  private final Database db;
  private final Catalog catalog;
  private final MediaStore mediaStore;
  private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  public RollsRomsBot(BotConfig config, Database db, Catalog catalog, MediaStore mediaStore) {
    this.config = config;
    this.db = db;
    this.catalog = catalog;
    this.mediaStore = mediaStore;
  }

  @Override
  public String getBotUsername() {
    return config.username();
  }

  @Override
  public String getBotToken() {
    return config.token();
  }

  @Override
  public void onUpdateReceived(Update update) {
    try {
      if (update.hasCallbackQuery()) {
        handleCallback(update.getCallbackQuery());
        return;
      }
      if (update.hasPreCheckoutQuery()) {
        handlePreCheckout(update.getPreCheckoutQuery());
        return;
      }
      if (update.hasMessage()) {
        handleMessage(update.getMessage());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void handleMessage(Message message) throws Exception {
    long chatId = message.getChatId();
    long userId = message.getFrom().getId();
    UserSession session = session(userId);
    session.userTag = userTag(message.getFrom());
    if (message.hasSuccessfulPayment()) {
      handleSuccessfulPayment(message, session);
      return;
    }

    if (message.isCommand()) {
      String command = extractCommand(message);
      if ("/start".equals(command)) {
        session.resetOrder();
        session.state = UserSession.State.IDLE;
        sendStart(chatId);
        return;
      }
      if ("/admin".equals(command)) {
        if (db.isAdmin(userId)) {
          sendAdminPanel(chatId);
        } else {
          sendText(chatId, "Доступ к админ панели ограничен.");
        }
        return;
      }
    }

    if (!message.hasText()) {
      return;
    }

    String text = message.getText().trim();
    switch (session.state) {
      case AWAITING_QTY -> handleQtyInput(chatId, session, text);
      case AWAITING_PHONE -> handlePhoneInput(chatId, session, text);
      case AWAITING_ADDRESS -> handleAddressInput(chatId, session, text);
      case ADMIN_ADD -> handleAdminAdd(chatId, session, text);
      case WAITING_PAYMENT -> sendText(chatId, "Я Вас не понимаю, пожалуйста, нажмите одну из кнопок выше");
      default -> sendText(chatId, "Я Вас не понимаю, пожалуйста, нажмите одну из кнопок выше");
    }
  }

  private void handleCallback(CallbackQuery callback) throws Exception {
    String data = callback.getData();
    long chatId = callback.getMessage().getChatId();
    long userId = callback.getFrom().getId();
    UserSession session = session(userId);
    session.userTag = userTag(callback.getFrom());

    if (CB_START_YES.equals(data)) {
      session.resetOrder();
      session.state = UserSession.State.VIEWING_CATALOG;
      sendCatalogIntro(chatId);
      sendCatalog(chatId, session, 0, null);
      answer(callback, null);
      return;
    }
    if (CB_START_NO.equals(data)) {
      sendNoThanks(chatId);
      answer(callback, null);
      return;
    }
    if (CB_CATALOG_PREV.equals(data)) {
      session.catalogIndex = Math.floorMod(session.catalogIndex - 1, catalog.size());
      sendCatalog(chatId, session, session.catalogIndex, callback.getMessage().getMessageId());
      answer(callback, null);
      return;
    }
    if (CB_CATALOG_NEXT.equals(data)) {
      session.catalogIndex = Math.floorMod(session.catalogIndex + 1, catalog.size());
      sendCatalog(chatId, session, session.catalogIndex, callback.getMessage().getMessageId());
      answer(callback, null);
      return;
    }
    if (data != null && data.startsWith(CB_CATALOG_ADD_PREFIX)) {
      String productId = data.substring(CB_CATALOG_ADD_PREFIX.length());
      Product product = catalog.findById(productId).orElse(null);
      if (product == null) {
        answer(callback, "Товар не найден");
        return;
      }
      if (!product.available()) {
        answer(callback, "❗️ Временно недоступно для заказа");
        return;
      }
      if (session.cart.add(productId)) {
        answer(callback, "Добавлено в корзину");
      } else {
        answer(callback, "Уже в корзине");
      }
      return;
    }
    if (CB_CATALOG_CHECKOUT.equals(data)) {
      if (session.cart.isEmpty()) {
        sendText(chatId, "Корзина пуста. Добавьте товар и попробуйте снова.");
      } else {
        session.state = UserSession.State.AWAITING_QTY;
        session.qtyIndex = 0;
        askNextQuantity(chatId, session);
      }
      answer(callback, null);
      return;
    }
    if (CB_PAY_NOW.equals(data)) {
      if (session.state != UserSession.State.WAITING_PAYMENT || session.pendingOrder == null) {
        answer(callback, "Сначала оформите заказ.");
        return;
      }
      if (session.invoiceSent) {
        answer(callback, "Счет уже отправлен.");
        return;
      }
      sendInvoice(chatId, session.pendingOrder);
      session.invoiceSent = true;
      answer(callback, null);
      return;
    }
    if (CB_BACK_START.equals(data)) {
      session.resetOrder();
      session.state = UserSession.State.IDLE;
      sendStart(chatId);
      answer(callback, null);
      return;
    }
    if (CB_ORDER_AGAIN.equals(data)) {
      session.resetOrder();
      session.state = UserSession.State.VIEWING_CATALOG;
      sendCatalogIntro(chatId);
      sendCatalog(chatId, session, session.catalogIndex, null);
      answer(callback, null);
      return;
    }
    if (CB_ADMIN_ADD.equals(data)) {
      if (db.isAdmin(userId)) {
        session.state = UserSession.State.ADMIN_ADD;
        sendText(chatId, "Введите Telegram ID пользователя для добавления в админы:");
      } else {
        sendText(chatId, "Доступ к админ панели ограничен.");
      }
      answer(callback, null);
      return;
    }
    if (CB_ADMIN_LIST.equals(data)) {
      if (db.isAdmin(userId)) {
        sendAdminList(chatId);
      } else {
        sendText(chatId, "Доступ к админ панели ограничен.");
      }
      answer(callback, null);
      return;
    }
    if (CB_NOOP.equals(data)) {
      answer(callback, null);
      return;
    }

    answer(callback, null);
  }

  private void handleQtyInput(long chatId, UserSession session, String text) throws Exception {
    int qty;
    try {
      qty = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      sendText(chatId, "Введите целое число больше 0.");
      return;
    }
    if (qty <= 0) {
      sendText(chatId, "Введите целое число больше 0.");
      return;
    }

    String productId = currentProductId(session);
    session.quantities.put(productId, qty);
    session.qtyIndex++;

    if (session.qtyIndex < session.cart.size()) {
      askNextQuantity(chatId, session);
    } else {
      session.state = UserSession.State.AWAITING_PHONE;
      sendText(chatId, "Введите номер телефона:");
    }
  }

  private void handlePhoneInput(long chatId, UserSession session, String text) throws Exception {
    if (text.isBlank()) {
      sendText(chatId, "Введите номер телефона:");
      return;
    }
    session.phone = text;
    session.state = UserSession.State.AWAITING_ADDRESS;
    sendText(chatId, "Укажите адрес доставки:");
  }

  private void handleAddressInput(long chatId, UserSession session, String text) throws Exception {
    if (text.isBlank()) {
      sendText(chatId, "Укажите адрес доставки:");
      return;
    }
    session.address = text;
    session.state = UserSession.State.WAITING_PAYMENT;
    session.invoiceSent = false;
    session.pendingOrder = buildOrder(chatId, session);

    sendOrderSummary(chatId, session.pendingOrder);
  }

  private void handleAdminAdd(long chatId, UserSession session, String text) throws Exception {
    long id;
    try {
      id = Long.parseLong(text.trim());
    } catch (NumberFormatException e) {
      sendText(chatId, "Нужен числовой Telegram ID. Попробуйте снова:");
      return;
    }
    db.addAdmin(id);
    session.state = UserSession.State.IDLE;
    sendText(chatId, "Админ добавлен: " + id);
    sendAdminPanel(chatId);
  }

  private void handlePreCheckout(PreCheckoutQuery query) throws TelegramApiException {
    AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
    answer.setPreCheckoutQueryId(query.getId());
    answer.setOk(true);
    execute(answer);
  }

  private void handleSuccessfulPayment(Message message, UserSession session) throws Exception {
    SuccessfulPayment payment = message.getSuccessfulPayment();
    if (session.pendingOrder == null || !Objects.equals(session.pendingOrder.payload(), payment.getInvoicePayload())) {
      sendText(message.getChatId(), "Оплата получена. Спасибо!");
      return;
    }

    Order order = session.pendingOrder;
    db.saveOrder(order);
    notifyAdmins(order);

    session.resetOrder();
    session.state = UserSession.State.IDLE;

    sendThankYou(message.getChatId());
  }

  private void sendStart(long chatId) throws TelegramApiException {
    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText("Здравствуйте! 😊🍫 Это чат-бот ромовых шариков «Rolls-Roms».\nХотите сделать заказ?");
    msg.setReplyMarkup(startKeyboard());
    executeAndTrack(msg, chatId, session(chatId));
  }

  private void sendNoThanks(long chatId) throws TelegramApiException {
    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText("Спасибо за интерес к Rolls-Roms! Если захотите попробовать, мы будем рады вашему заказу.");
    msg.setReplyMarkup(singleButton("🛍 Сделать заказ", CB_START_YES));
    executeAndTrack(msg, chatId, session(chatId));
  }

  private void sendCatalogIntro(long chatId) throws TelegramApiException {
    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText("Выберите вид десерта:");
    executeAndTrack(msg, chatId, session(chatId));
  }

  private void sendCatalog(long chatId, UserSession session, int index, Integer messageId) throws TelegramApiException {
    Product product = catalog.get(index);
    session.catalogIndex = index;

    StringBuilder captionBuilder = new StringBuilder(product.title());
    if (product.available()) {
      if (product.description() != null && !product.description().isBlank()) {
        captionBuilder.append("\n").append(product.description());
      }
      captionBuilder.append("\n\n<i>Цена за 1 шт:\n1 шт — 430 ₽\n2–3 шт — 370 ₽\n4–6 шт — 340 ₽</i>");
    } else {
      captionBuilder.append("\n\n❗️ Временно недоступно для заказа");
    }
    String caption = captionBuilder.toString();
    InlineKeyboardMarkup keyboard = catalogKeyboard(index, catalog.size(), product);

    if (messageId == null) {
      SendPhoto sendPhoto = new SendPhoto();
      sendPhoto.setChatId(chatId);
      sendPhoto.setCaption(caption);
      sendPhoto.setParseMode("HTML");
      sendPhoto.setReplyMarkup(keyboard);
      sendPhoto.setPhoto(buildInputFile(product.image()));
      Message message = executeAndTrack(sendPhoto, chatId, session);
      cachePhotoId(product.image(), message);
      session.catalogMessageId = message.getMessageId();
      return;
    }

    InputMediaPhoto media = new InputMediaPhoto();
    mediaStore.getFileId(product.image()).ifPresentOrElse(
        media::setMedia,
        () -> media.setMedia(mediaStore.getFile(product.image()), product.image())
    );
    media.setCaption(caption);
    media.setParseMode("HTML");

    EditMessageMedia edit = new EditMessageMedia();
    edit.setChatId(chatId);
    edit.setMessageId(messageId);
    edit.setMedia(media);
    edit.setReplyMarkup(keyboard);
    execute(edit);
  }

  private void askNextQuantity(long chatId, UserSession session) throws TelegramApiException {
    String productId = currentProductId(session);
    Product product = catalog.findById(productId).orElseThrow();

    SendPhoto sendPhoto = new SendPhoto();
    sendPhoto.setChatId(chatId);
    sendPhoto.setCaption("Введите количество " + product.title() + ":");
    sendPhoto.setPhoto(buildInputFile(product.smallImage()));

    Message message = executeAndTrack(sendPhoto, chatId, session);
    cachePhotoId(product.smallImage(), message);
  }

  private void sendOrderSummary(long chatId, Order order) throws TelegramApiException {
    StringBuilder text = new StringBuilder();
    text.append("Состав заказа:\n");
    for (OrderItem item : order.items()) {
      int lineTotal = item.unitPrice() * item.quantity();
      text.append("- ").append(item.product().title())
          .append(" x").append(item.quantity())
          .append(" = ").append(formatMoney(lineTotal, order.currency()))
          .append("\n");
    }
    text.append("\nИтого: ").append(formatMoney(order.total(), order.currency())).append("\n\n");
    text.append("Если все верно, нажмите «Оплатить» ниже. Если нужно исправить, нажмите кнопку и вернитесь в меню.");

    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText(text.toString());
    msg.setReplyMarkup(summaryKeyboard());
    executeAndTrack(msg, chatId, session(chatId));
  }

  private void scheduleInvoice(long chatId, UserSession session) {
    session.cancelInvoiceTask();
    session.invoiceTask = scheduler.schedule(() -> {
      if (session.state != UserSession.State.WAITING_PAYMENT || session.invoiceSent) {
        return;
      }
      try {
        sendInvoice(chatId, session.pendingOrder);
        session.invoiceSent = true;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }, 3, TimeUnit.SECONDS);
  }

  private void sendInvoice(long chatId, Order order) throws TelegramApiException {
    SendInvoice invoice = new SendInvoice();
    invoice.setChatId(chatId);
    invoice.setTitle("Заказ Rolls Roms");
    invoice.setDescription("Оплата заказа");
    invoice.setPayload(order.payload());
    invoice.setProviderToken(config.providerToken());
    invoice.setCurrency(order.currency());
    invoice.setPrices(toPrices(order));
    invoice.setProviderData(ReceiptBuilder.build(order, config.taxSystemCode()));
    executeAndTrack(invoice, chatId, session(chatId));
  }

  private void sendAdminPanel(long chatId) throws TelegramApiException {
    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    rows.add(List.of(button("➕ Добавить админа", CB_ADMIN_ADD)));
    rows.add(List.of(button("📋 Список админов", CB_ADMIN_LIST)));
    keyboard.setKeyboard(rows);

    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText("Админ панель:");
    msg.setReplyMarkup(keyboard);
    executeAndTrack(msg, chatId, session(chatId));
  }

  private void sendAdminList(long chatId) throws Exception {
    List<Long> admins = db.listAdmins();
    StringBuilder text = new StringBuilder("Админы:\n");
    for (Long admin : admins) {
      text.append("- ").append(admin).append("\n");
    }
    sendText(chatId, text.toString());
  }

  private void sendThankYou(long chatId) throws TelegramApiException {
    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText("Спасибо, что выбрали Rolls Roms! Ваш заказ уже в обработке.");
    msg.setReplyMarkup(singleButton("🛍 Заказать еще", CB_ORDER_AGAIN));
    executeAndTrack(msg, chatId, session(chatId));
  }

  private void notifyAdmins(Order order) throws Exception {
    String tag = order.tag();
    StringBuilder text = new StringBuilder();
    text.append("Новый заказ!\n");
    text.append("Тег: ").append(tag).append("\n");
    text.append("Номер телефона: ").append(order.phone()).append("\n");
    text.append("Адрес доставки: ").append(order.address()).append("\n");
    text.append("Состав заказа:\n");
    for (OrderItem item : order.items()) {
      text.append("- ").append(item.product().title())
          .append(" x").append(item.quantity())
          .append("\n");
    }

    for (Long adminId : db.listAdmins()) {
      SendMessage msg = new SendMessage();
      msg.setChatId(adminId);
      msg.setText(text.toString());
      execute(msg);
    }
  }

  private void sendText(long chatId, String text) throws TelegramApiException {
    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText(text);
    executeAndTrack(msg, chatId, session(chatId));
  }

  private Message executeAndTrack(BotApiMethod<Message> method, long chatId, UserSession session)
      throws TelegramApiException {
    return execute(method);
  }

  private Message executeAndTrack(SendPhoto method, long chatId, UserSession session)
      throws TelegramApiException {
    return execute(method);
  }

  private InlineKeyboardMarkup startKeyboard() {
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    rows.add(List.of(
        button("🛍 Сделать заказ", CB_START_YES)
    ));
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    markup.setKeyboard(rows);
    return markup;
  }

  private InlineKeyboardMarkup singleButton(String text, String data) {
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    markup.setKeyboard(List.of(List.of(button(text, data))));
    return markup;
  }

  private InlineKeyboardMarkup summaryKeyboard() {
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    rows.add(List.of(button("💳 Оплатить", CB_PAY_NOW)));
    rows.add(List.of(button("🔙 Вернуться в меню", CB_BACK_START)));
    markup.setKeyboard(rows);
    return markup;
  }

  private InlineKeyboardMarkup catalogKeyboard(int index, int total, Product product) {
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    String counter = "📦 " + (index + 1) + "/" + total;

    rows.add(List.of(
        button("⬅️", CB_CATALOG_PREV),
        button(counter, CB_NOOP),
        button("➡️", CB_CATALOG_NEXT)
    ));
    if (product.available()) {
      rows.add(List.of(
          button("🧺 Добавить в корзину", CB_CATALOG_ADD_PREFIX + product.id())
      ));
    } else {
      rows.add(List.of(
          button("⛔ Недоступно", CB_NOOP)
      ));
    }
    rows.add(List.of(
        button("✅ Оформить заказ", CB_CATALOG_CHECKOUT)
    ));

    markup.setKeyboard(rows);
    return markup;
  }

  private InlineKeyboardButton button(String text, String data) {
    InlineKeyboardButton button = new InlineKeyboardButton();
    button.setText(text);
    button.setCallbackData(data);
    return button;
  }

  private void answer(CallbackQuery callback, String text) throws TelegramApiException {
    org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer =
        new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
    answer.setCallbackQueryId(callback.getId());
    if (text != null && !text.isBlank()) {
      answer.setText(text);
      answer.setShowAlert(false);
    }
    execute(answer);
  }

  private String extractCommand(Message message) {
    if (message.getEntities() == null) {
      return message.getText();
    }
    for (MessageEntity entity : message.getEntities()) {
      if ("bot_command".equals(entity.getType())) {
        return message.getText().substring(entity.getOffset(), entity.getOffset() + entity.getLength());
      }
    }
    return message.getText();
  }

  private UserSession session(long userId) {
    return sessions.computeIfAbsent(userId, id -> new UserSession());
  }

  private InputFile buildInputFile(String resource) {
    return new InputFile(mediaStore.getFile(resource));
  }

  private void cachePhotoId(String resource, Message message) {
    if (message == null || message.getPhoto() == null || message.getPhoto().isEmpty()) {
      return;
    }
    PhotoSize best = message.getPhoto().stream()
        .max(Comparator.comparing(PhotoSize::getFileSize, Comparator.nullsLast(Integer::compareTo)))
        .orElse(null);
    if (best != null) {
      mediaStore.cacheFileId(resource, best.getFileId());
    }
  }

  private String currentProductId(UserSession session) {
    return new ArrayList<>(session.cart).get(session.qtyIndex);
  }

  private Order buildOrder(long chatId, UserSession session) {
    List<OrderItem> items = new ArrayList<>();
    int totalQty = 0;
    for (String productId : session.cart) {
      int qty = session.quantities.getOrDefault(productId, 1);
      totalQty += qty;
    }
    int unitPrice = priceForTotalQty(totalQty);
    int total = 0;
    for (String productId : session.cart) {
      Product product = catalog.findById(productId).orElseThrow();
      int qty = session.quantities.getOrDefault(productId, 1);
      items.add(new OrderItem(product, qty, unitPrice));
      total += unitPrice * qty;
    }
    String payload = "order:" + chatId + ":" + Instant.now().toEpochMilli();
    String tag = session.userTag != null ? session.userTag : ("id:" + chatId);
    return new Order(chatId, tag, session.phone, session.address, items, total, config.currency(), payload);
  }

  private List<LabeledPrice> toPrices(Order order) {
    List<LabeledPrice> prices = new ArrayList<>();
    for (OrderItem item : order.items()) {
      int lineTotal = item.unitPrice() * item.quantity();
      String label = item.product().title() + " x" + item.quantity();
      prices.add(new LabeledPrice(label, lineTotal));
    }
    return prices;
  }

  private String formatMoney(int amount, String currency) {
    return String.format(Locale.US, "%.2f %s", amount / 100.0, currency);
  }

  private int priceForTotalQty(int totalQty) {
    if (totalQty <= 1) {
      return 43000;
    }
    if (totalQty <= 3) {
      return 37000;
    }
    if (totalQty <= 6) {
      return 34000;
    }
    return 31000;
  }

  private String userTag(User user) {
    if (user == null) {
      return null;
    }
    if (user.getUserName() != null && !user.getUserName().isBlank()) {
      return "@" + user.getUserName();
    }
    StringBuilder sb = new StringBuilder();
    if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
      sb.append(user.getFirstName().trim());
    }
    if (user.getLastName() != null && !user.getLastName().isBlank()) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(user.getLastName().trim());
    }
    if (sb.length() > 0) {
      return sb.toString();
    }
    return "id:" + user.getId();
  }
}
