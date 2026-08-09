package com.guessmarket.engine.util;

import com.guessmarket.engine.model.Event;
import com.guessmarket.engine.model.Option;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import java.util.ArrayList;
import java.util.List;

public class XmlParser {
    /**
     * Helper method to extract text from an XML element and trim whitespace.
     */
    private static String getTagValue (String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if(nodeList != null && nodeList.getLength() > 0){
            Node node = nodeList.item(0);
            return node.getTextContent().trim(); //Remove spaces
        }
        return null;
    }

    public static Event parseEventNode(Element eventElement) throws Exception{
        /**
         * Creates Event object based on XML content
         */

        // 1. Extract the name
        String eventName = eventElement.getAttribute("name").trim();

        // 2. Extract standard elements
        String idString = getTagValue("id", eventElement);
        int id = Integer.parseInt(idString);

        String description = getTagValue("description", eventElement);

        // 3. Extract the commission and its type attribute
        NodeList commissionNodes = eventElement.getElementsByTagName("comision");
        Element commissionElement = (Element) commissionNodes.item(0);

        String commissionType = commissionElement.getAttribute("type").trim();
        int commissionRate = Integer.parseInt(commissionElement.getTextContent().trim());

        // Validate commission rule (0 < comRate < 90)
        if (commissionRate < 0 || commissionRate > 90) {
            throw new Exception("Commission must be between 0 and 90. Found: " + commissionRate);
        }

        // 4. Extract the b parameter for LMSR
        String bString = getTagValue("b", eventElement);
        int bParameter = Integer.parseInt(bString);

        // 5. Extract the nested <GM-option> tags
        List<Option> optionsList = new ArrayList<>();
        NodeList optionNodes = eventElement.getElementsByTagName("GM-option");

        for (int i = 0; i < optionNodes.getLength(); i++) {
            Element optionElement = (Element) optionNodes.item(i);

            // Get the text inside the tag (e.g., "Yes")
            String optionName = optionElement.getTextContent().trim();

            // Create a new Option object and add it to the list
            Option newOption = new Option(optionName);
            optionsList.add(newOption);
        }

        // Assemble and return the Event
        Event newEvent = new Event(id, eventName, description, commissionRate,optionsList, commissionType, bParameter);

        return newEvent;
    }

}
