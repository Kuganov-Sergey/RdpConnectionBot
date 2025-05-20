package com.kuganov.soft.bot.work.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@ControllerAdvice
@Slf4j
public class BotExceptionHandler {

    @ExceptionHandler(TelegramApiException.class)
    public void handleTelegramApiException(TelegramApiException e) {
        log.error("Telegram API Error: " + e.getMessage(), e);
    }
}
