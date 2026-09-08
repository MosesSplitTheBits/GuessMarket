package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class EventRefXml {

    @XmlAttribute(name = "id")
    private int id;

    public int getId() {
        return id;
    }
}
