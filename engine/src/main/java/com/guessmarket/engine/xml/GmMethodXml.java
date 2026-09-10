package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmMethodXml {

    @XmlElement(name = "GM-LMSR")
    private GmLmsrXml gmLmsr;

    @XmlElement(name = "GM-order-book")
    private GmOrderBookXml gmOrderBook;

    public GmOrderBookXml getGmOrderBook() {
        return gmOrderBook;
    }

    public GmLmsrXml getLmsr(){
        return gmLmsr;
    }


}
