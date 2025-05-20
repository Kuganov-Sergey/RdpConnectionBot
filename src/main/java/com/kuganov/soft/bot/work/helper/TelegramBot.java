package com.kuganov.soft.bot.work.helper;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
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
    private String chatId;
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
            this.chatId = update.getMessage().getChatId().toString();
            String messageText = update.getMessage().getText();

            switch (messageText) {
                case "/help" -> sendHelpMessage();
                case "/start" -> startMonitoring();
                case "/stop" -> stopMonitoring();
                case "/status" -> sendCurrentStatus();
                default -> sendMessage("Неизвестная команда. Доступные команды: /start, /stop, /status, /help");
            }
        }
    }

    private void startMonitoring() {
        if (monitoringEnabled) {
            sendMessage("Мониторинг уже запущен");
            return;
        }

        monitoringEnabled = true;
        sendMessage("🚀 Мониторинг соединения запущен. IP: " + hostIp);

        executorService.scheduleAtFixedRate(this::checkConnection, 0, 5, TimeUnit.SECONDS);
    }

    private void stopMonitoring() {
        if (!monitoringEnabled) {
            sendMessage("Мониторинг не был запущен");
            return;
        }

        monitoringEnabled = false;
        sendMessage("🛑 Мониторинг остановлен");
    }

    private void checkConnection() {
        if (!monitoringEnabled || chatId == null) return;

        try {
            boolean currentStatus = InetAddress.getByName(hostIp).isReachable(5000);

            if (currentStatus != lastConnectionStatus) {
                if (currentStatus) {
                    sendMessage("✅ Соединение с ПК восстановлено!");
                    log.info("Соединение восстановлено");
                } else {
                    sendMessage("⚠️ ПК стал недоступен!");
                    log.warn("ПК недоступен");
                }
                lastConnectionStatus = currentStatus;
            }
        } catch (Exception e) {
            log.error("Ошибка проверки соединения: {}", e.getMessage());
        }
    }

    private void sendCurrentStatus() {
        try {
            boolean currentStatus = InetAddress.getByName(hostIp).isReachable(5000);
            sendMessage(currentStatus ? "🟢 ПК доступен" : "🔴 ПК недоступен");
        } catch (Exception e) {
            sendMessage("❌ Ошибка проверки статуса: " + e.getMessage());
        }
    }

    private void sendHelpMessage() {
        String helpText = """
        🤖 Помощь по боту:
        /start - запустить мониторинг соединения
        /stop - остановить мониторинг
        /status - текущий статус соединения
        /help - эта справка
        
        Текущий IP для мониторинга: %s
        """.formatted(hostIp);
        sendMessage(helpText);
    }

    private void sendMessage(String text) {
        if (chatId == null) return;

        SendMessage message = new SendMessage(chatId, text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdownNow();
    }
}


