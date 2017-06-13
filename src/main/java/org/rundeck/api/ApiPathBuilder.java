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
package org.rundeck.api;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.rundeck.api.util.ParametersUtil;

/**
 * Builder for API paths
 * 
 * @author Vincent Behar
 */
class ApiPathBuilder {

    /** Internally, we store everything in a {@link StringBuilder} */
    private final StringBuilder apiPath;

    /** When POSTing, we can add attachments */
    private final Map<String, InputStream> attachments;

    /** Marker for using the right separator between parameters ("?" or "&") */
    private boolean firstParamDone = false;

    /**
     * Build a new instance, for the given "path" (the "path" is the part before the parameters. The path and the
     * parameters are separated by a "?")
     * 
     * @param paths elements of the path
     */
    public ApiPathBuilder(String... paths) {
        apiPath = new StringBuilder();
        attachments = new HashMap<String, InputStream>();
        if (paths != null) {
            for (String path : paths) {
                if (StringUtils.isNotBlank(path)) {
                    append(path);
                }
            }
        }
    }

    /**
     * Append the given parameter (key and value). This will only append the parameter if it is not blank (null, empty
     * or whitespace), and make sure to add the right separator ("?" or "&") before. The key and value will be separated
     * by the "=" character. Also, the value will be url-encoded.
     * 
     * @param key of the parameter. Must not be null or empty
     * @param value of the parameter. May be null/empty/blank. Will be url-encoded.
     * @return this, for method chaining
     */
    public ApiPathBuilder param(String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            appendSeparator();
            append(key);
            append("=");
            append(ParametersUtil.urlEncode(value));
        }
        return this;
    }

    /**
     * Append the given parameter (key and value). This will only append the parameter if it is not null, and make sure
     * to add the right separator ("?" or "&") before. The key and value will be separated by the "=" character. Also,
     * the value will be converted to lower-case.
     * 
     * @param key of the parameter. Must not be null or empty
     * @param value of the parameter. May be null
     * @return this, for method chaining
     */
    public ApiPathBuilder param(String key, Enum<?> value) {
        if (value != null) {
            param(key, StringUtils.lowerCase(value.toString()));
        }
        return this;
    }

    /**
     * Append the given parameter (key and value). This will only append the parameter if it is not null, and make sure
     * to add the right separator ("?" or "&") before. The key and value will be separated by the "=" character.
     * 
     * @param key of the parameter. Must not be null or empty
     * @param value of the parameter. May be null
     * @return this, for method chaining
     */
    public ApiPathBuilder param(String key, Date value) {
        if (value != null) {
            param(key, value.getTime());
        }
        return this;
    }

    /**
     * Append the given parameter (key and value). This will only append the parameter if it is not null, and make sure
     * to add the right separator ("?" or "&") before. The key and value will be separated by the "=" character.
     * 
     * @param key of the parameter. Must not be null or empty
     * @param value of the parameter. May be null
     * @return this, for method chaining
     */
    public ApiPathBuilder param(String key, Long value) {
        if (value != null) {
            param(key, value.toString());
        }
        return this;
    }

    /**
     * Append the given parameter (key and value). This will only append the parameter if it is not null, and make sure
     * to add the right separator ("?" or "&") before. The key and value will be separated by the "=" character.
     * 
     * @param key of the parameter. Must not be null or empty
     * @param value of the parameter. May be null
     * @return this, for method chaining
     */
    public ApiPathBuilder param(String key, Integer value) {
        if (value != null) {
            param(key, value.toString());
        }
        return this;
    }

    /**
     * Append the given parameter (key and value). This will only append the parameter if it is not null, and make sure
     * to add the right separator ("?" or "&") before. The key and value will be separated by the "=" character.
     * 
     * @param key of the parameter. Must not be null or empty
     * @param value of the parameter. May be null
     * @return this, for method chaining
     */
    public ApiPathBuilder param(String key, Boolean value) {
        if (value != null) {
            param(key, value.toString());
        }
        return this;
    }

    /**
     * Append the given node filters, only if it is not null/empty
     * 
     * @param nodeFilters may be null/empty
     * @return this, for method chaining
     * @see ParametersUtil#generateNodeFiltersString(Properties)
     */
    public ApiPathBuilder nodeFilters(Properties nodeFilters) {
        String filters = ParametersUtil.generateNodeFiltersString(nodeFilters);
        if (StringUtils.isNotBlank(filters)) {
            appendSeparator();
            append(filters);
        }
        return this;
    }

    /**
     * When POSTing a request, add the given {@link InputStream} as an attachment to the content of the request. This
     * will only add the stream if it is not null.
     * 
     * @param name of the attachment. Must not be null or empty
     * @param stream. May be null
     * @return this, for method chaining
     */
    public ApiPathBuilder attach(String name, InputStream stream) {
        if (stream != null) {
            attachments.put(name, stream);
        }
        return this;
    }

    /**
     * @return all attachments to be POSTed, with their names
     */
    public Map<String, InputStream> getAttachments() {
        return attachments;
    }

    @Override
    public String toString() {
        return apiPath.toString();
    }

    /**
     * Append the given string
     * 
     * @param str to append
     */
    private void append(String str) {
        apiPath.append(str);
    }

    /**
     * Append the right separator "?" or "&" between 2 parameters
     */
    private void appendSeparator() {
        if (firstParamDone) {
            append("&");
        } else {
            append("?");
            firstParamDone = true;
        }
    }

}
