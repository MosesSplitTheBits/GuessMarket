package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.*;



  @XmlAccessorType(XmlAccessType.FIELD)
  public class GmOrderBookXml {

      @XmlAttribute(name = "allow-mint")
      private boolean allowMint;

     @XmlAttribute(name = "initial")
     private int initial;

      @XmlAttribute(name = "d")
      private int d;


      public boolean getAllowMint(){
        return allowMint;
      }

      public int getInitial(){
          return initial;
      }

      public int getD(){
          return d;
      }


  }


