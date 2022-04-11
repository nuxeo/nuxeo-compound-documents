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

import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_DOCTYPE_DETECTION_OPERATION;
import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION;
import static org.nuxeo.runtime.model.Descriptor.UNIQUE_DESCRIPTOR_ID;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
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
        var parentDoc = session.getDocument(new PathRef(parent));
        try (var zip = new ZipFile(getFile(archive))) {
            List<ZipEntry> entries = zip.stream()
                                        .filter(this::isAllowedEntry)
                                        .sorted(Comparator.comparing(ZipEntry::getName))
                                        .collect(Collectors.toList());
            // Avoid giving the whole blobs
            List<String> entryNames = entries.stream().map(ZipEntry::getName).collect(Collectors.toList());
            String compoundType = runScript(COMPOUND_DOCTYPE_DETECTION_OPERATION, session, parentDoc,
                    Map.of("entries", entryNames));
            DocumentModel compoundDoc = session.createDocumentModel(parent, compoundDocName, compoundType);
            compoundDoc.setPropertyValue("dc:title", compoundDocName);
            final DocumentModel finalDoc = session.createDocument(compoundDoc);
            String compoundFolderType = runScript(COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION, session, finalDoc,
                    Map.of());
            entries.forEach(entry -> createEntry(session, finalDoc, zip, entry, compoundFolderType));
            return finalDoc;
        } catch (IOException e) {
            var message = String.format("Failed to create compound document for archive: %s in parent: %s",
                    archive.getFilename(), parent);
            throw new NuxeoException(message, e);
        }
    }

    protected File getFile(Blob archive) {
        if (archive.getFile() == null) {
            try (var inputStream = archive.getStream()) {
                return Blobs.createBlob(inputStream).getFile();
            } catch (IOException e) {
                var message = String.format("Failed to read file from archive: %s", archive.getFilename());
                throw new NuxeoException(message, e);
            }
        }
        return archive.getFile();
    }

    protected String runScript(String scriptId, CoreSession session, DocumentModel doc, Map<String, ?> params) {
        AutomationService automationService = Framework.getService(AutomationService.class);
        try (OperationContext ctx = new OperationContext(session)) {
            ctx.setInput(doc);
            var result = (String) automationService.run(ctx, scriptId, params);
            if (result == null) {
                throw new OperationException("The operation returned null");
            }
            return result;
        } catch (OperationException e) {
            var message = String.format("Error while running script: %s for input: %s", scriptId, doc);
            throw new NuxeoException(message, e);
        }
    }

    protected boolean isAllowedEntry(ZipEntry entry) {
        Path path = new Path(entry.getName());
        IgnoredFilesDescriptor ignoredFiles = getDescriptor(XP_IGNORED_FILES, UNIQUE_DESCRIPTOR_ID);
        return !ignoredFiles.ignore(path);
    }

    protected void createEntry(CoreSession session, DocumentModel compoundDoc, ZipFile zip, ZipEntry entry,
            String compoundFolderType) {
        Path entryPath = new Path(entry.getName());
        Path entryParentPath = entryPath.removeLastSegments(1);
        String parentDocPath = compoundDoc.getPath().append(entryParentPath).toString();
        String entryDocName = entryPath.lastSegment();
        if (entry.isDirectory()) {
            var doc = session.createDocumentModel(parentDocPath, entryDocName, compoundFolderType);
            compoundDoc.setPropertyValue("dc:title", entryDocName);
            session.createDocument(doc);
        } else {
            var blob = new ZipEntryBlob(zip, entry);
            blob.setFilename(entryDocName);
            var ctx = FileImporterContext.builder(session, blob, parentDocPath).build();
            try {
                Framework.getService(FileManager.class).createOrUpdateDocument(ctx);
            } catch (NuxeoException | IOException e) {
                String message = String.format("Failed to create document for entry: %s in: %s for archive: %s",
                        entry.getName(), compoundDoc, zip.getName());
                if (e instanceof NuxeoException) {
                    NuxeoException ne = (NuxeoException) e;
                    ne.addInfo(message);
                    throw ne;
                }
                throw new NuxeoException(message, e);
            }
        }
    }

}
