package com.kuganov.soft.bot.work.helper;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final String hostIp;
    private final Set<String> userChatIds = Collections.synchronizedSet(new HashSet<>());
    private boolean monitoringEnabled = false;
    private boolean lastConnectionStatus = false;
    private final ScheduledExecutorService executorService;

    public TelegramBot(BotConfig botConfig) {
        super(botConfig.getToken());
        this.botConfig = botConfig;
        this.hostIp = botConfig.getHostIp();
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String chatId = update.getMessage().getChatId().toString();
            // Добавляем chatId пользователя в список
            userChatIds.add(chatId);

            String messageText = update.getMessage().getText();

            switch (messageText) {
                case "/help" -> sendHelpMessage(chatId);
                case "/start" -> startMonitoring(chatId);
                case "/stop" -> stopMonitoring(chatId);
                case "/status" -> sendCurrentStatus(chatId);
                default -> sendMessage(chatId, "Неизвестная команда. Доступные команды: /start, /stop, /status, /help");
            }
        }
    }

    // Методы для отправки сообщений конкретному пользователю
    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
        }
    }

    private void sendHelpMessage(String chatId) {
        String helpText = """
        🤖 Помощь по боту:
        /start - запустить мониторинг соединения
        /stop - остановить мониторинг
        /status - текущий статус соединения
        /help - эта справка
        
        Текущий IP для мониторинга: %s
        """.formatted(hostIp);
        sendMessage(chatId, helpText);
    }

    private void startMonitoring(String chatId) {
        if (monitoringEnabled) {
            sendMessage(chatId, "Мониторинг уже запущен");
            return;
        }

        monitoringEnabled = true;
        sendMessage(chatId, "🚀 Мониторинг соединения запущен. IP: " + hostIp);
        executorService.scheduleAtFixedRate(this::checkConnection, 0, 5, TimeUnit.SECONDS);
    }

    private void stopMonitoring(String chatId) {
        if (!monitoringEnabled) {
            sendMessage(chatId, "Мониторинг не был запущен");
            return;
        }

        monitoringEnabled = false;
        sendMessage(chatId, "🛑 Мониторинг остановлен");
    }

    private void sendCurrentStatus(String chatId) {
        try {
            boolean currentStatus = InetAddress.getByName(hostIp).isReachable(5000);
            sendMessage(chatId, currentStatus ? "🟢 ПК доступен" : "🔴 ПК недоступен");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка проверки статуса: " + e.getMessage());
        }
    }

    // Метод для рассылки сообщения всем пользователям
    public void broadcastMessage(String text) {
        synchronized (userChatIds) {
            for (String chatId : userChatIds) {
                sendMessage(chatId, text);
            }
        }
    }

    private void checkConnection() {
        if (!monitoringEnabled) return;

        try {
            boolean currentStatus = InetAddress.getByName(hostIp).isReachable(5000);
            if (currentStatus != lastConnectionStatus) {
                String message;
                if (currentStatus) {
                    message = "✅ Соединение с ПК восстановлено!";
                } else {
                    message = "⚠️ ПК стал недоступен!";
                }
                broadcastMessage(message);
                lastConnectionStatus = currentStatus;
            }
        } catch (Exception e) {
            log.error("Ошибка проверки соединения: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdownNow();
    }
}


