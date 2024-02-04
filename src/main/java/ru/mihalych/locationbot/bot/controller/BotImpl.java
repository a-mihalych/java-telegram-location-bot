package ru.mihalych.locationbot.bot.controller;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethodMessage;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.mihalych.locationbot.bot.components.BotCommands;
import ru.mihalych.locationbot.bot.config.BotConfig;
import ru.mihalych.locationbot.bot.service.BotService;

import java.util.List;

@Service
@Slf4j
public class BotImpl extends TelegramLongPollingBot implements BotCommands {

    private final BotConfig botConfig;
    private final BotService botService;

    public BotImpl(BotConfig botConfig, BotService botService) {
        this.botConfig = botConfig;
        this.botService = botService;
        try {
            execute(new SetMyCommands(COMMANDS_MENU, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("\n!!! class BotImpl. Конструктор, создание меню: {}", e.getMessage());
        }
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(getChatId());
        sendMessage.setText("Был запущен location-bot!");
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("\n!!! class BotImpl. Конструктор, отправка сообщения о запуске бота: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    public String getBotName() {
        return botConfig.getBotName();
    }

    private long getChatId() {
        return Long.parseLong(botConfig.getChatId());
    }

    private long getGeneralChatId() {
        return Long.parseLong(botConfig.getGeneralChatId());
    }

    @Override
    public void onUpdateReceived(@NotNull Update update) {
        log.info("\n+++ update: {}", update);
        send(botService.parserUpdate(update, getChatId()));
    }

    private void send(List<Object> sends) {
        if ((sends.size() == 1) && (sends.get(0) instanceof SendMessage)) {
            SendMessage sendMessage = (SendMessage) sends.get(0);
            if (TXT_START.equals(sendMessage.getText())) {
                try {
                    execute(new SetMyCommands(COMMANDS_MENU, new BotCommandScopeDefault(), null));
                } catch (TelegramApiException e) {
                    log.error("\n!!! class BotImpl. Метод send, создание меню: {}", e.getMessage());
                }
            }
        }
        for (Object send : sends) {
            BotApiMethodMessage botApiMethodMessage = (BotApiMethodMessage) send;
            try {
                execute(botApiMethodMessage);
            } catch (TelegramApiException e) {
                log.error("\n!!! class BotImpl. Метод send, send: {}\n!!! {}", send, e.getMessage());
            }
        }
    }
}
