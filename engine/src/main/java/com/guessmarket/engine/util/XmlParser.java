package com.guessmarket.engine.util;

import com.guessmarket.engine.api.EngineException;
import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.TradingMethod;
import com.guessmarket.engine.xml.GmEventXml;
import com.guessmarket.engine.xml.GmLmsrXml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XmlParser {

    /**
     * Builds a domain Event from its JAXB-parsed XML representation,
     * validating the business rules the XML schema itself doesn't enforce
     * (commission range, option count, Order Book config).
     */
    public static Event mapToEvent(GmEventXml xmlEvent) {

        // Ex3: the name is the event's identity now (schema v3 dropped <id>),
        // so it must be present, and surrounding spaces must not make two
        // otherwise-identical names count as different (spec: trim()).
        String name = xmlEvent.getName() == null ? "" : xmlEvent.getName().trim();
        if (name.isEmpty()) {
            throw new EngineException("Every event must have a non-empty name.");
        }

        String description = xmlEvent.getDescription() == null ? "" : xmlEvent.getDescription().trim();

        if (xmlEvent.getCommission() == null) {
            throw new EngineException("Event '" + name + "' is missing its commission.");
        }
        int rate = xmlEvent.getCommission().getRate();
        if (rate < 0 || rate > 90) {
            throw new EngineException("Event '" + name + "': commission must be between 0 and 90. Found: " + rate);
        }

        String commissionType = xmlEvent.getCommission().getType();
        if (!"on-close".equals(commissionType) && !"on-purchase".equals(commissionType)) {
            throw new EngineException("Event '" + name + "': commission type must be on-close or on-purchase. Found: " + commissionType);
        }

        // Exactly 2 options (YES/NO-style). The Ex3 spec's goals list mentions
        // multi-option events, but the grader confirmed it is NOT required,
        // and the official GM-EX3-Schema.xsd caps GM-option at maxOccurs="2".
        List<String> rawOptions = xmlEvent.getOptions();
        if (rawOptions == null || rawOptions.size() != 2) {
            throw new EngineException("Event '" + name + "' must have exactly 2 options, found: "
                    + (rawOptions == null ? 0 : rawOptions.size()));
        }
        List<Option> optionsList = new ArrayList<>();
        // Option names must be unique within an event: LMSR's close-out pays
        // winners by matching TradeRecord option names.
        Set<String> seenOptionNames = new HashSet<>();
        for (String rawOption : rawOptions) {
            String optionName = rawOption == null ? "" : rawOption.trim();
            if (optionName.isEmpty()) {
                throw new EngineException("Event '" + name + "' has an empty option name.");
            }
            if (!seenOptionNames.add(optionName)) {
                throw new EngineException("Event '" + name + "' has a duplicate option: " + optionName);
            }
            optionsList.add(new Option(optionName));
        }

        if (xmlEvent.getMethod() == null) {
            throw new EngineException("Event '" + name + "' is missing its trading method.");
        }
        GmLmsrXml lmsr = xmlEvent.getMethod().getLmsr();

        TradingMethod method;
        int bParameter;
        boolean allowMint = false;
        int initial = -1;
        int d = -1;
        if (lmsr != null) {
            method = TradingMethod.LMSR;
            bParameter = lmsr.getB();
            if (bParameter <= 0) {
                throw new EngineException("Event '" + name + "': b must be a positive integer. Found: " + bParameter);
            }
        } else if (xmlEvent.getMethod().getGmOrderBook() != null) {
            method = TradingMethod.ORDER_BOOK;
            bParameter = 0; // unused for Order Book — it has its own config below
            allowMint = xmlEvent.getMethod().getGmOrderBook().getAllowMint();
            initial = xmlEvent.getMethod().getGmOrderBook().getInitial();
            d = xmlEvent.getMethod().getGmOrderBook().getD();

            if (initial < 0 || d <= 0) {
                throw new EngineException("Event '" + name + "': initial must be >= 0 and d > 0.");
            }
        } else {
            throw new EngineException("Event '" + name + "' must use either GM-LMSR or GM-order-book.");
        }

        Event event = new Event(name, description, rate, optionsList, commissionType, bParameter, method);

        if (method == TradingMethod.ORDER_BOOK) {
            event.setOrderBookConfig(allowMint, initial, d);
        }

        return event;
    }
}
