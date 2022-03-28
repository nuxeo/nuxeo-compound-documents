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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_DOCTYPE_DETECTION_OPERATION;
import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.assertCompoundDocument;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.getBadArchive;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.getTestArchive;

import java.io.IOException;
import java.util.zip.ZipException;

import javax.inject.Inject;

import org.apache.commons.io.FilenameUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(CompoundDocumentsFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestCompoundDocumentImporter {

    @Inject
    protected CoreSession session;

    @Inject
    protected FileManager fileManager;

    @Test
    public void testCreateCompoundDocument() throws IOException {
        Blob blob = getTestArchive();
        FileImporterContext context = FileImporterContext.builder(session, blob, "/").build();

        DocumentModel doc = fileManager.createOrUpdateDocument(context);

        assertCompoundDocument(doc);
    }

    @Test
    public void testBadArchive() throws IOException {
        Blob blob = getBadArchive();
        FileImporterContext context = FileImporterContext.builder(session, blob, "/").build();

        NuxeoException e = assertThrows(NuxeoException.class, () -> fileManager.createOrUpdateDocument(context));

        String compoundDocName = FilenameUtils.removeExtension(blob.getFilename());
        assertEquals(String.format("Failed to create CompoundDocument for archive: %s.zip in parent: %s",
                compoundDocName, context.getParentPath()), e.getMessage());
        ZipException cause = (ZipException) e.getCause();
        assertEquals("zip file is empty", cause.getMessage());
        assertNull(cause.getCause());
    }

    @Test
    @Deploy("org.nuxeo.compound.documents:operations-test-contrib.xml")
    public void testCustomCompoundDocumentType() throws IOException {
        Blob blob = getTestArchive();
        FileImporterContext context = FileImporterContext.builder(session, blob, "/").build();

        DocumentModel doc = fileManager.createOrUpdateDocument(context);
        assertCompoundDocument(doc, "CustomCompoundDocument", "CustomCompoundDocumentFolder");
    }

    @Test
    @Deploy("org.nuxeo.compound.documents:bad-compound-document-operation-contrib.xml")
    public void testBadCustomCompoundDocumentType() throws IOException {
        Blob blob = getTestArchive();
        FileImporterContext context = FileImporterContext.builder(session, blob, "/").build();

        NuxeoException e = assertThrows(NuxeoException.class, () -> fileManager.createOrUpdateDocument(context));

        String expected = String.format("Error while running script: %s for input: %s",
                COMPOUND_DOCTYPE_DETECTION_OPERATION, session.getRootDocument());
        assertEquals(expected, e.getMessage());
        OperationException cause = (OperationException) e.getCause();
        assertEquals("Failed to invoke operation " + COMPOUND_DOCTYPE_DETECTION_OPERATION, cause.getMessage());
    }

    @Test
    @Deploy("org.nuxeo.compound.documents:bad-compound-document-folder-operation-contrib.xml")
    public void testBadCustomCompoundDocumentFolderType() throws IOException {
        Blob blob = getTestArchive();
        FileImporterContext context = FileImporterContext.builder(session, blob, "/").build();

        NuxeoException e = assertThrows(NuxeoException.class, () -> fileManager.createOrUpdateDocument(context));

        String expectedPrefix = String.format("Error while running script: %s for input: ",
                COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION);
        assertTrue(e.getMessage().startsWith(expectedPrefix));
        OperationException cause = (OperationException) e.getCause();
        assertEquals("Failed to invoke operation " + COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION, cause.getMessage());
    }

}
