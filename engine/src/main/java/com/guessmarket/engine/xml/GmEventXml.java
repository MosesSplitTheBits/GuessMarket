package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmEventXml {

    @XmlAttribute(name = "name")
    private String name;

    @XmlElement(name = "id")
    private int id;

    @XmlElement(name = "description")
    private String description;

    @XmlElement(name = "comision")
    private CommissionXml commission;

    @XmlElementWrapper(name = "GM-options")
    @XmlElement(name = "GM-option")
    private List<String> options;

    @XmlElement(name = "GM-method")
    private GmMethodXml method;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CommissionXml getCommission() {
        return commission;
    }

    public int getId() {
        return id;
    }

    public List<String> getOptions() {
        return options;
    }

    public GmMethodXml getMethod() {
        return method;
    }
}
