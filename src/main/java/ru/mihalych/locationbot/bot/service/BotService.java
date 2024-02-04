package ru.mihalych.locationbot.bot.service;

import com.vdurmont.emoji.EmojiParser;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVenue;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.mihalych.locationbot.bot.components.BotCommands;

import java.util.*;

@Service
public class BotService {

    private Map<Long, SendVenue> goals = new HashMap<>();
    private int detectionRadiusMeters = 50;
    private final String TXT_RESPONSE = "Боту не удалось распознать отправленное:\n";

    public List<Object> parserUpdate(Update update, long chatId) {
        Message message = null;
        if (update.hasMessage() || update.hasEditedMessage()) {
            if (update.hasMessage()) {
                message = update.getMessage();
            } else {
                message = update.getEditedMessage();
            }
            chatId = message.getChatId();
        }
        if (message != null && (message.hasText() || message.hasLocation())) {
            if (message.hasText()) {
                return parserTxt(message.getText().trim(), chatId);
            } else {
                return parserLocation(message.getLocation(), chatId);
            }
        }
        return List.of(sendMessage(chatId, TXT_RESPONSE + update));
    }

    private List<Object> parserTxt(String txt, long chatId) {
        if (txt.startsWith("/")) {
            switch (txt) {
                case "/start":
                    return List.of(sendMessage(chatId, BotCommands.TXT_START));
                case "/help":
                    return List.of(sendMessage(chatId, BotCommands.TXT_HELP));
                default:
                    return List.of(sendMessage(chatId, TXT_RESPONSE + txt));
            }
        }
        String[] coordinates = txt.trim().split(" *[( ,)] *");
        if (coordinates.length != 2) {
            return List.of(sendMessage(chatId, TXT_RESPONSE + txt));
        }
        try {
            double latitude = Double.parseDouble(coordinates[0]);
            double longitude = Double.parseDouble(coordinates[1]);
            String address = String.format("Широта: %f\nДолгота: %f", latitude, longitude);
            SendVenue sendVenue = sendVenue(chatId, latitude, longitude, "Цель", address);
            goals.put(chatId, sendVenue);
            return List.of(sendVenue);
        } catch (NumberFormatException e) {
            return List.of(sendMessage(chatId, TXT_RESPONSE + txt));
        }
    }

    private List<Object> parserLocation(Location location, long chatId) {
        List<Object> sends = new ArrayList<>();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        String address = String.format("Широта: %f\nДолгота: %f", latitude, longitude);
        SendVenue sendVenue = sendVenue(chatId, latitude, longitude, "Текущее местоположение", address);
        sends.add(sendVenue);

        SendVenue sendVenueGoal = goals.get(chatId);
        if (sendVenueGoal == null) {
            sends.add(sendMessage(chatId,
                              "Отсутствует цель. Нужно ввести координаты (широта долгота):\n11.111111 22.222222"));
            return sends;
        }
        sends.add(sendVenueGoal);

        double distance = distance(sendVenue.getLatitude(), sendVenue.getLongitude(),
                                   sendVenueGoal.getLatitude(), sendVenueGoal.getLongitude());
        if ((distance * 1000) < detectionRadiusMeters) {
            sends.add(sendMessage(chatId, EmojiParser.parseToUnicode(":checkered_flag:")));
            sends.add(sendMessage(chatId, String.format("Осталось менее %d метров. Цель достигнута!", detectionRadiusMeters)));
        }

        String meterOrKm = "м";
        if (distance < 1) {
            distance *= 1000;
        } else {
            meterOrKm = "к" + meterOrKm;
        }
        double azimuth = azimuth(sendVenue.getLatitude(), sendVenue.getLongitude(),
                                 sendVenueGoal.getLatitude(), sendVenueGoal.getLongitude());
        String txt = String.format("Расстояние до цели по прямой: %.2f %s\nНаправление движения (азимут): %.2f°",
                                   distance, meterOrKm, azimuth);
        sends.add(sendMessage(chatId, txt));
        return sends;
    }

    private double distance(double latitude1, double longitude1, double latitude2, double longitude2) {
        int radius = 6371;
        // todo Варинт 1
//        double sin1 = Math.sin(Math.toRadians(latitude1));
//        double sin2 = Math.sin(Math.toRadians(latitude2));
//        double cos1 = Math.cos(Math.toRadians(latitude1));
//        double cos2 = Math.cos(Math.toRadians(latitude2));
//        double cos3 = Math.cos(Math.toRadians(longitude1 - longitude2));
//        return radius * Math.acos(sin1 * sin2 + cos1 * cos2 * cos3);
        // todo Варинт 2
        double sin1_2 = Math.pow(Math.sin(Math.toRadians(latitude2 - latitude1) / 2), 2);
        double sin2_2 = Math.pow(Math.sin(Math.toRadians(longitude1 - longitude2) / 2), 2);
        double cos1 = Math.cos(Math.toRadians(latitude1));
        double cos2 = Math.cos(Math.toRadians(latitude2));
        return radius * Math.asin(Math.sqrt(sin1_2 + cos1 * cos2 * sin2_2)) * 2;
    }

    private double azimuth(double latitude1, double longitude1, double latitude2, double longitude2) {
        double cos1 = Math.cos(latitude1);
        double cos2 = Math.cos(latitude2);
        double sin1 = Math.sin(latitude1);
        double sin2 = Math.sin(latitude2);
        double cos3 = Math.cos(longitude2 - longitude1);
        double sin3 = Math.sin(longitude2 - longitude1);
        double x = cos1 * sin2 - sin1 * cos2 * cos3;
        double y = sin3 * cos2;
        double result = Math.atan2(y, x) * 180 / Math.PI;
        return (result + 360) % 360;
    }

    private SendMessage sendMessage(long chatId, String txt) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(txt);
        return sendMessage;
    }

    private SendVenue sendVenue(long chatId, double latitude, double longitude, String title, String Address) {
        SendVenue sendVenue = new SendVenue();
        sendVenue.setChatId(chatId);
        sendVenue.setTitle(title);
        sendVenue.setAddress(Address);
        sendVenue.setLatitude(latitude);
        sendVenue.setLongitude(longitude);
        return sendVenue;
    }
}
