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

import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_DOCTYPE;
import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_FOLDER_DOCTYPE;
import static org.nuxeo.runtime.model.Descriptor.UNIQUE_DESCRIPTOR_ID;

import java.io.IOException;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.ZipEntryBlob;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

/** @since 2021.0 */
public class CompoundDocumentServiceImpl extends DefaultComponent implements CompoundDocumentService {

    protected static final String XP_IGNORED_FILES = "ignoredFiles";

    @Override
    public DocumentModel createCompoundDocument(CoreSession session, String parent, Blob archive) {
        String compoundDocName = FilenameUtils.removeExtension(archive.getFilename());
        DocumentModel compoundDoc = session.createDocumentModel(parent, compoundDocName, COMPOUND_DOCTYPE);
        compoundDoc.setPropertyValue("dc:title", compoundDocName);
        compoundDoc = session.createDocument(compoundDoc);
        createCompoundDocument(compoundDoc, archive);
        return compoundDoc;
    }

    protected void createCompoundDocument(DocumentModel compoundDoc, Blob archive) {
        try (ZipFile zip = new ZipFile(archive.getFile())) {
            zip.stream()
               .filter(this::isAllowedEntry)
               .sorted(Comparator.comparing(ZipEntry::getName))
               .forEach(entry -> createEntry(compoundDoc, archive.getFilename(), zip, entry));
        } catch (IOException e) {
            String message = String.format(
                    "Failed to create CompoundDocument: %s. something went wrong with archive: %s.",
                    compoundDoc.getName(), archive.getFilename());
            throw new NuxeoException(message, e);
        }
    }

    protected boolean isAllowedEntry(ZipEntry entry) {
        Path path = new Path(entry.getName());
        IgnoredFilesDescriptor ignoredFiles = getDescriptor(XP_IGNORED_FILES, UNIQUE_DESCRIPTOR_ID);
        return !ignoredFiles.ignore(path);
    }

    protected void createEntry(DocumentModel compoundDoc, String archiveName, ZipFile zip, ZipEntry entry) {
        Path entryPath = new Path(entry.getName());
        Path entryParentPath = entryPath.removeLastSegments(1);
        String parentDocPath = compoundDoc.getPath().append(entryParentPath).toString();
        String entryDocName = entryPath.lastSegment();
        var session = compoundDoc.getCoreSession();
        if (entry.isDirectory()) {
            var doc = session.createDocumentModel(parentDocPath, entryDocName, COMPOUND_FOLDER_DOCTYPE);
            session.createDocument(doc);
        } else {
            var blob = new ZipEntryBlob(zip, entry);
            blob.setFilename(entryDocName);
            FileImporterContext ctx = FileImporterContext.builder(session, blob, parentDocPath).build();
            try {
                Framework.getService(FileManager.class).createOrUpdateDocument(ctx);
            } catch (IOException e) {
                String message = String.format(
                        "Failed to create CompoundDocument: %s. something went wrong with archive: %s at entry: %s.",
                        compoundDoc.getName(), archiveName, entry.getName());
                throw new NuxeoException(message, e);
            }
        }
    }

}
