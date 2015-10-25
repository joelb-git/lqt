/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.basistech.lucene.tools;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import java.util.List;
import java.util.Map;

abstract class Formatter {
    protected final Format format;
    protected final boolean suppressNames;

    enum Format {
        MULTILINE("multiline"),
        TABULAR("tabular"),
        JSON("json"),
        JSON_PRETTY("json-pretty");

        static Map<String, Format> ids;
        private String id;

        static {
            ids = Maps.newHashMap();
            for (Format format : Format.values()) {
                ids.put(format.id, format);
            }
        }

        Format(String id) {
            this.id = id;
        }

        static Format fromName(String id) {
            if (!ids.containsKey(id)) {
                throw new RuntimeException("Unsupported format: " + id);
            }
            return ids.get(id);
        }
    }

    Formatter(Format format, boolean suppressNames) {
        this.format = format;
        this.suppressNames = suppressNames;
    }

    abstract String format(List<String> names, Multimap<String, String> data);

    Format getFormat() {
        return format;
    }

    boolean suppressNames() {
        return suppressNames;
    }

    static Formatter newInstance(Format format, boolean suppressNames) {
        Formatter formatter;
        switch (format) {
        case MULTILINE:
            formatter = new MultilineFormatter(format, suppressNames);
            break;
        case TABULAR:
            formatter = new TabFormatter(format, suppressNames);
            break;
        case JSON:
            formatter = new JsonFormatter(format, false);
            break;
        case JSON_PRETTY:
            formatter = new JsonFormatter(format, true);
            break;
        default:
            throw new RuntimeException("Unsupported format: " + format);
        }
        return formatter;
    }
}
