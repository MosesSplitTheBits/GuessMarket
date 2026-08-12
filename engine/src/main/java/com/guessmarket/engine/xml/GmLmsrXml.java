package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmLmsrXml {

    @XmlElement(name = "b")
    private int b;

    public int getB(){
        return b;
    }
}
