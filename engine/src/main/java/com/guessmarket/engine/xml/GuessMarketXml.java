package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;

import java.util.List;

@XmlRootElement(name = "Guess-Market")
@XmlAccessorType(XmlAccessType.FIELD)
public class GuessMarketXml {

    @XmlElementWrapper(name = "GM-events")
    @XmlElement(name = "GM-event")
    private List<GmEventXml>  events;

    @XmlElementWrapper(name = "GM-users")
    @XmlElement(name = "GM-user")
    private List<GmUserXml> users;

    public List<GmEventXml> getEvents() {
        return events;
    }

    public List<GmUserXml> getUsers() {
        return users;
    }

}
