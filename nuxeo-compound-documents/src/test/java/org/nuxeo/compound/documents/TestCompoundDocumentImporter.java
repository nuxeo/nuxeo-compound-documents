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

import static org.nuxeo.compound.documents.CompoundDocumentUtils.assertCompoundDocument;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.makeTestZipBlob;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.filemanager")
@Deploy("org.nuxeo.ecm.platform.types")
@Deploy("org.nuxeo.compound.documents")
public class TestCompoundDocumentImporter {

    @Inject
    protected CoreSession session;

    @Inject
    protected FileManager fileManager;

    @Test
    public void testCreateCompoundDocument() throws IOException {
        Blob blob = makeTestZipBlob();
        FileImporterContext context = FileImporterContext.builder(session, blob, "/").build();

        fileManager.createOrUpdateDocument(context);
        DocumentModel doc = session.getDocument(new PathRef("/" + blob.getFilename()));

        assertCompoundDocument(doc);
    }

}
