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
package org.nuxeo.compound.documents.constants;

/** @since 2021.0 */
public final class CompoundDocumentConstants {

    private CompoundDocumentConstants() {
        // Utility class
    }

    public static final String COMPOUND_DOCUMENT_FACET = "CompoundDocument";

    public static final String COMPOUND_DOCTYPE_DETECTION_OPERATION = "javascript.GetCompoundDocumentType";

    public static final String COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION = "javascript.GetCompoundDocumentFolderType";

    public static final String COMPOUND_PREVIEW_DETECTION_OPERATION = "javascript.GetCompoundDocumentPreview";

}
