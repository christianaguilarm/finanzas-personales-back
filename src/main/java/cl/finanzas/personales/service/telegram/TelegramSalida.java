package cl.finanzas.personales.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;

public record TelegramSalida(String metodo, JsonNode payload) {}
