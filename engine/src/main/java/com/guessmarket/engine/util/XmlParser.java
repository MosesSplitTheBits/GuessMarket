package com.guessmarket.engine.util;

import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.model.TradingMethod;
import com.guessmarket.engine.model.User;
import com.guessmarket.engine.xml.EventRefXml;
import com.guessmarket.engine.xml.GmEventXml;
import com.guessmarket.engine.xml.GmLmsrXml;
import com.guessmarket.engine.xml.GmUserXml;

import java.util.ArrayList;
import java.util.List;

public class XmlParser {

    /**
     * Builds a domain Event from its JAXB-parsed XML representation,
     * validating the business rules the XML schema itself doesn't enforce
     * (commission range, exact option count).
     */
    public static Event mapToEvent(GmEventXml xmlEvent) throws Exception {

        int id = xmlEvent.getId();
        String name = xmlEvent.getName();
        String description = xmlEvent.getDescription();

        int rate = xmlEvent.getCommission().getRate();
        if (rate < 0 || rate > 90) {
            throw new Exception("Commission must be between 0 and 90. Found: " + rate);
        }

        String commissionType = xmlEvent.getCommission().getType();

        if (xmlEvent.getOptions().size() != 2) {
            throw new Exception("Event must have exactly 2 options, found: " + xmlEvent.getOptions().size());
        }
        List<Option> optionsList = new ArrayList<>();

        for (String option : xmlEvent.getOptions()) {
            Option newOption = new Option(option);
            optionsList.add(newOption);
        }

        GmLmsrXml lmsr = xmlEvent.getMethod().getLmsr();

        TradingMethod method;
        int bParameter;
        if (lmsr != null) {
            method = TradingMethod.LMSR;
            bParameter = lmsr.getB();
        } else {
            method = TradingMethod.ORDER_BOOK;
            bParameter = 0; // unused until Order Book trading is implemented
        }

        return new Event(id, name, description, rate, optionsList, commissionType, bParameter, method);
    }

    public static User mapToUser(GmUserXml xmlUser) throws Exception {

        String name = xmlUser.getName();
        double initCash = xmlUser.getInitialCash();
        if (initCash < 0){
            throw new Exception("Initial Cash must be greater than zero.");
        }

        User newUser = new User(name, initCash);

        List<EventRefXml> managedEvents = xmlUser.getManagedEvents();

        if (managedEvents != null) {
            for (EventRefXml ref : managedEvents) {
                newUser.addManagedEvent(ref.getId());
            }
        }

        return newUser;

    }

}
