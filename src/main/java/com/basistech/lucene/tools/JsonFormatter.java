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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class JsonFormatter extends Formatter {
    private final ObjectWriter writer;

    JsonFormatter(Format format, boolean prettyPrint) {
        super(format, false);
        if (prettyPrint) {
            writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
        } else {
            writer = new ObjectMapper().writer();
        }
    }

    @Override
    public String format(List<String> names, Multimap<String, String> data) {
        // We could use the GuavaModule to format the Multimap directly:
        //   mapper.registerModule(new GuavaModule());
        // but single-valued fields (the vast majority) would be written as
        // an array with one value.  We copy the Multimap to a normal Map to
        // avoid this.
        Map<String, Object> nonGuavaMap = Maps.newHashMap();
        for (String name : data.keySet()) {
            Collection<String> values = data.get(name);
            if (values.size() == 1) {
                nonGuavaMap.put(name, Iterables.getOnlyElement(values));
            } else if (values.isEmpty()) {
                nonGuavaMap.put(name, "null");
            } else {
                nonGuavaMap.put(name, values);
            }
        }

        try {
            return writer.writeValueAsString(nonGuavaMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
