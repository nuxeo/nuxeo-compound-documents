/* 
 * (C) Copyright 2022 Nuxeo (http://nuxeo.com/) and others.
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
 *
 */
package org.nuxeo.compound.documents;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/** @since 2021.0 */
@XObject("file")
public class IgnoredFileDescriptor {

    @XNode
    protected String file;

    @XNode("@regex")
    protected boolean regex;

    protected volatile Pattern pattern;

    public boolean ignore(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return true;
        }
        if (regex) {
            if (pattern == null) {
                synchronized (this) {
                    if (pattern == null) {
                        pattern = Pattern.compile(file);
                    }
                }
            }
            return pattern.matcher(fileName).matches();
        } else {
            return fileName.equals(file);
        }
    }

}
