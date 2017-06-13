/*
 * Copyright 2011 Vincent Behar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rundeck.api.parser;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Node;

/**
 * Parser for a single {@link String}
 * 
 * @author Vincent Behar
 */
public class StringParser implements XmlNodeParser<String> {

    private String xpath;

    public StringParser() {
        super();
    }

    /**
     * @param xpath of the string element if it is not the root node
     */
    public StringParser(String xpath) {
        super();
        this.xpath = xpath;
    }

    @Override
    public String parseXmlNode(Node node) {
        Node strNode = xpath != null ? node.selectSingleNode(xpath) : node;

        return StringUtils.trimToNull(strNode.getStringValue());
    }

}
