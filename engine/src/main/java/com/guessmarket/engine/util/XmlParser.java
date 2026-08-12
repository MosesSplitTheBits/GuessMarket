package com.guessmarket.engine.util;

import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.Option;
import com.guessmarket.engine.xml.GmEventXml;
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

        int bParameter = xmlEvent.getMethod().getLmsr().getB();

        return new Event(id, name, description, rate, optionsList, commissionType, bParameter);
    }

}
