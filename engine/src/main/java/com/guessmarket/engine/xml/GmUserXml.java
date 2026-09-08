package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmUserXml {

    @XmlAttribute(name = "name")
    private String name;

    @XmlElement(name = "initial-cash")
    private int initialCash;

    @XmlElementWrapper(name = "GM-market-maker")
    @XmlElement(name = "event")
    private List<EventRefXml> managedEvents;

    public String getName() {
        return name;
    }

    public int getInitialCash() {
        return initialCash;
    }

    public List<EventRefXml> getManagedEvents() {
        return managedEvents;
    }
}
