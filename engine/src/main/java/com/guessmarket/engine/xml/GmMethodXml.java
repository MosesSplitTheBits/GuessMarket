package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmMethodXml {

    @XmlElement(name = "GM-LMSR")
    private GmLmsrXml gmLmsr;

    public GmLmsrXml getLmsr(){
        return gmLmsr;
    }


}
