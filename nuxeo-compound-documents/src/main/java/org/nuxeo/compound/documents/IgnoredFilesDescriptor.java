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

import java.util.HashSet;
import java.util.Set;

import org.nuxeo.common.utils.Path;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

/** @since 2021.0 */
@XObject("files")
public class IgnoredFilesDescriptor implements Descriptor {

    @XNodeList(value = "file", type = HashSet.class, componentType = IgnoredFileDescriptor.class)
    protected Set<IgnoredFileDescriptor> ignoredFileDescriptors;

    @Override
    public String getId() {
        return UNIQUE_DESCRIPTOR_ID;
    }

    public boolean ignore(Path path) {
        for (String segment : path.segments()) {
            if (ignoredFileDescriptors.stream().anyMatch(i -> i.ignore(segment))) {
                return true;
            }
        }
        return false;
    }
}
