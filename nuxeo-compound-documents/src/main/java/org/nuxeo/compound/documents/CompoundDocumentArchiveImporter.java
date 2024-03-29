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

import org.nuxeo.compound.documents.service.CompoundDocumentService;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.service.extension.DefaultFileImporter;
import org.nuxeo.runtime.api.Framework;

/** @since 2021.0 */
public class CompoundDocumentArchiveImporter extends DefaultFileImporter {

    private static final long serialVersionUID = 1L;

    @Override
    public DocumentModel createOrUpdate(FileImporterContext context) {
        return Framework.getService(CompoundDocumentService.class)
                        .createCompoundDocument(context.getSession(), context.getParentPath(), context.getBlob());
    }
}
