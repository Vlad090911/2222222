package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Connection;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;

import java.util.Comparator;
import java.util.List;

public class MyTelegramBot extends TelegramLongPollingBot {

    private final DatabaseSearchService searchService;
    private final LocalDate trialSubscriptionEndDate;
    private String lastQuery = "";


    public MyTelegramBot(Connection booksConnection, Connection usersConnection) throws SQLException {
        this.searchService = new DatabaseSearchService(booksConnection, usersConnection);
        this.trialSubscriptionEndDate = LocalDate.now().plusDays(3);
    }


    @Override
    public String getBotUsername() {
        return "libraryofworlds_bot"; // Вкажіть ім'я вашого бота
    }

    @Override
    public String getBotToken() {
        return "6903678778:AAGz5mcc7SJarnDI04ZEMUS7ItaZjUQF3kA"; // Вкажіть токен вашого бота
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Логування всього оновлення
        System.out.println("Отримано оновлення: " + update.toString());

        if (update.hasMessage() && update.getMessage().hasText()) {
            // Логування всього повідомлення
            System.out.println("Повідомлення: " + update.getMessage().toString());

            // Витягування інформації з повідомлення
            Message message = update.getMessage();
            String messageText = message.getText();
            long chatId = message.getChatId();
            int userId = Math.toIntExact(message.getFrom().getId());
            String userName = message.getFrom().getUserName();
            String firstName = message.getFrom().getFirstName();
            String lastName = message.getFrom().getLastName();

            // Логування інформації про користувача
            System.out.println("ID користувача: " + userId);
            System.out.println("Ім'я користувача: " + (firstName != null ? firstName : "Невідоме") + " " + (lastName != null ? lastName : "Невідоме"));
            System.out.println("Username: @" + (userName != null ? userName : "Невідоме"));
            System.out.println("Текст повідомлення: " + messageText);

            try {
                boolean isFirstRequest = checkAndSetTrialSubscription(userId, chatId); // Перевірка і установка пробної підписки
                if (isFirstRequest) {
                    sendTextMessage(chatId, "Вітаємо! Ви отримали 3 дні пробної підписки. Ви можете скористатися пошуком книг протягом цього часу.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendTextMessage(chatId, "Не вдалося зберегти інформацію про користувача.");
                return;
            }

            if (messageText.startsWith("/download_")) {
                if (!isSubscriptionActive(userId)) {
                    sendTextMessage(chatId, "Ваша підписка закінчилась. Для завантаження файлів потрібна активна підписка.");
                    return;
                }

                String fileIdStr = messageText.replace("/download_", "").trim();
                long fileId;
                try {
                    fileId = Long.parseLong(fileIdStr);
                } catch (NumberFormatException e) {
                    sendTextMessage(chatId, "Невірний формат команди.");
                    return;
                }
                forwardFileFromChannel(chatId, fileId);
            } else {
                handleTextMessage(update.getMessage());
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }


    private boolean checkAndSetTrialSubscription(int userId, long chatId) throws SQLException {
        User user = searchService.getUser(userId);
        if (user == null) {
            // Користувача немає в базі даних, додаємо новий з пробною підпискою
            searchService.saveUser(userId, chatId, null, null, trialSubscriptionEndDate.toString(), "");
            return true; // Перший запит
        }
        return false; // Користувач вже існує
    }

    private boolean isSubscriptionActive(int userId) {
        try {
            User user = searchService.getUser(userId);
            if (user == null) {
                return false; // Користувач не знайдений
            }
            String subscriptionEndDateStr = user.getsubscriptionEndDate(); // Це має бути рядок
            if (subscriptionEndDateStr == null || subscriptionEndDateStr.isEmpty()) {
                return false; // Якщо рядок пустий, повертаємо false
            }
            LocalDate subscriptionEndDate = LocalDate.parse(subscriptionEndDateStr);
            return !LocalDate.now().isAfter(subscriptionEndDate);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void handleTextMessage(Message message) {
        String keyword = message.getText();
        long chatId = message.getChatId();
        lastQuery = keyword;  // Збереження запиту
        List<Book> books;

        try {
            books = searchService.searchBooks(keyword); // Викликаємо метод пошуку книг
        } catch (SQLException e) {
            e.printStackTrace();
            sendTextMessage(chatId, "Сталася помилка при пошуку книг.");
            return;
        }

        if (!books.isEmpty()) {
            books.sort(Comparator.comparingInt(this::parseSeriesNumber));
            SendMessage sendMessage = formatBooksMessageWithButtons(books, 1, 5, chatId);
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else {
            sendTextMessage(chatId, "Нічого не знайдено.");
        }
    }

    private int parseSeriesNumber(Book book) {
        try {
            return Integer.parseInt(book.getSeriesNumber());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        String currentText = update.getCallbackQuery().getMessage().getText();
        InlineKeyboardMarkup currentMarkup = (InlineKeyboardMarkup) update.getCallbackQuery().getMessage().getReplyMarkup();

        if (callbackData.startsWith("page:")) {
            int pageNumber = Integer.parseInt(callbackData.split(":")[1]);
            List<Book> books;

            try {
                books = searchService.searchBooks(lastQuery); // Використовуємо останній запит для пошуку
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            books.sort(Comparator.comparingInt(this::parseSeriesNumber));

            int totalBooks = books.size();
            int totalPages = (int) Math.ceil((double) totalBooks / 5);

            if (pageNumber > totalPages || pageNumber < 1) {
                sendTextMessage(chatId, "Немає книг на цій сторінці.");
                return;
            }

            String messageText = formatBooksMessageWithButtonsText(books, pageNumber, 5);
            InlineKeyboardMarkup newMarkup = createPageButtons(books, pageNumber, 5);

            if (messageText.equals(currentText) && newMarkup.equals(currentMarkup)) {
                return;
            }

            EditMessageText newMessage = new EditMessageText();
            newMessage.setChatId(chatId);
            newMessage.setMessageId(messageId);
            newMessage.setText(messageText);
            newMessage.setReplyMarkup(newMarkup);

            try {
                execute(newMessage);
            } catch (TelegramApiException e) {
                if (e.getMessage().contains("message is not modified")) {
                    System.out.println("Message is not modified. No update needed.");
                } else {
                    e.printStackTrace();
                }
            }
        }
    }

    private SendMessage formatBooksMessageWithButtons(List<Book> books, int pageNumber, int pageSize, long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(formatBooksMessageWithButtonsText(books, pageNumber, pageSize));
        sendMessage.setReplyMarkup(createPageButtons(books, pageNumber, pageSize));
        return sendMessage;
    }

    private String formatBooksMessageWithButtonsText(List<Book> books, int pageNumber, int pageSize) {
        StringBuilder response = new StringBuilder("Бібліотека світів\n\n");

        int start = (pageNumber - 1) * pageSize;
        int end = Math.min(start + pageSize, books.size());

        for (int i = start; i < end; i++) {
            Book book = books.get(i);
            response.append("📚 Назва: ").append(book.getTitle()).append("\n")
                    .append("🖊️ Автор: ").append(book.getAuthors()).append("\n");

            if (book.getSeriesName() != null && !book.getSeriesName().isEmpty()) {
                response.append("📖 Серія: ").append(book.getSeriesName());
                if (book.getSeriesNumber() != null && !book.getSeriesNumber().isEmpty()) {
                    response.append(" № ").append(book.getSeriesNumber());
                }
                response.append("\n");
            }

            response.append("🔗 Скачать: /download_").append(book.getDownloadLink()).append("\n\n");
        }

        return response.toString();
    }

    private InlineKeyboardMarkup createPageButtons(List<Book> books, int currentPage, int pageSize) {
        int totalPages = (int) Math.ceil((double) books.size() / pageSize);

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (int i = 1; i <= totalPages; i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();

            if (i == currentPage) {
                button.setText("\" " + i + " \"");
            } else {
                button.setText(String.valueOf(i));
            }

            button.setCallbackData("page:" + i);
            row.add(button);

            if (row.size() == 8 || i == totalPages) {
                buttons.add(row);
                row = new ArrayList<>();
            }
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);
        return markup;
    }

    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void forwardFileFromChannel(long chatId, long fileId) {
        try {
            String channelId = "-1002150324720";  // Замініть на ID вашого каналу
            forwardMessage(chatId, channelId, (int) fileId);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendTextMessage(chatId, "Не вдалося переслати файл.");
        }
    }

    private void forwardMessage(long chatId, String fromChatId, int messageId) throws TelegramApiException {
        ForwardMessage forwardMessage = new ForwardMessage();
        forwardMessage.setChatId(chatId);
        forwardMessage.setFromChatId(fromChatId);
        forwardMessage.setMessageId(messageId);

        execute(forwardMessage);
    }
}