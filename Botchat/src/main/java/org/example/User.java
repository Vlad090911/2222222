package org.example;

import java.time.LocalDate;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class User {
    private int id;
    private long chatId;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private String subscriptionStatus;
    private String lastQuery;

    // Конструктор з параметрами
    public User(int id, long chatId, LocalDate subscriptionStartDate, LocalDate subscriptionEndDate, String subscriptionStatus, String lastQuery) {
        this.id = id;
        this.chatId = chatId;
        this.subscriptionStartDate = subscriptionStartDate;
        this.subscriptionEndDate = subscriptionEndDate;
        this.subscriptionStatus = subscriptionStatus;
        this.lastQuery = lastQuery;
    }

    // Конструктор без параметрів
    public User() {
        // Пустий конструктор
    }

    // Геттери і сеттери
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public LocalDate getSubscriptionStartDate() {
        return subscriptionStartDate;
    }

    public void setSubscriptionStartDate(LocalDate subscriptionStartDate) {
        this.subscriptionStartDate = subscriptionStartDate;
    }

    public LocalDate getSubscriptionEndDate() {
        return subscriptionEndDate;
    }

    public void setSubscriptionEndDate(LocalDate subscriptionEndDate) {
        this.subscriptionEndDate = subscriptionEndDate;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public String getLastQuery() {
        return lastQuery;
    }

    public void setLastQuery(String lastQuery) {
        this.lastQuery = lastQuery;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", chatId=" + chatId +
                ", subscriptionStartDate=" + subscriptionStartDate +
                ", subscriptionEndDate=" + subscriptionEndDate +
                ", subscriptionStatus='" + subscriptionStatus + '\'' +
                ", lastQuery='" + lastQuery + '\'' +
                '}';
    }

    // Метод для перевірки, чи підписка дійсна
    public boolean isSubscriptionValid() {
        if (subscriptionEndDate == null) {
            return false;
        }
        return !LocalDate.now().isAfter(subscriptionEndDate);
    }

    // Метод для перевірки, чи підписка скоро закінчиться
    public boolean isSubscriptionExpiringSoon(int days) {
        if (subscriptionEndDate == null) {
            return false;
        }
        long daysUntilExpiration = ChronoUnit.DAYS.between(LocalDate.now(), subscriptionEndDate);
        return daysUntilExpiration <= days;
    }


    public String getsubscriptionEndDate() {
        if (subscriptionEndDate == null) {
            return null; // Або поверніть якийсь дефолтний рядок, якщо потрібно
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return subscriptionEndDate.format(formatter);
    }

}
