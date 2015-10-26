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

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.List;

public class MultilineFormatter extends Formatter {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    MultilineFormatter(Format format, boolean suppressNames) {
        super(format, suppressNames);
    }

    @Override
    public String format(List<String> names, Multimap<String, String> data) {
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            Collection<String> values = data.get(name);
            if (values.size() == 1) {
                if (!suppressNames) {
                    sb.append(name);
                    sb.append(": ");
                }
                sb.append(Iterables.getOnlyElement(values));
            } else if (values.isEmpty()) {
                if (!suppressNames) {
                    sb.append(name);
                    sb.append(": ");
                }
                sb.append("null");
            } else {
                for (String value : values) {
                    if (!suppressNames) {
                        sb.append(name);
                        sb.append(": ");
                    }
                    sb.append(value);
                    sb.append(MultilineFormatter.LINE_SEPARATOR);
                }
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append(MultilineFormatter.LINE_SEPARATOR);
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
