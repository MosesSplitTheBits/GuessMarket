package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class CommissionXml {

    @XmlValue
    private int rate;

    @XmlAttribute(name = "type")
    private String type;

    public int getRate(){
        return rate;
    }

    public String getType(){
        return type;
    }



}
