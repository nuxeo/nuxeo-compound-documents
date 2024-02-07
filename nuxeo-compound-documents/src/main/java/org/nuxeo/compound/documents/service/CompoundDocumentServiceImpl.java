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
package org.nuxeo.compound.documents.service;

import static org.nuxeo.compound.documents.constants.CompoundDocumentConstants.COMPOUND_DOCTYPE_DETECTION_OPERATION;
import static org.nuxeo.compound.documents.constants.CompoundDocumentConstants.COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION;
import static org.nuxeo.compound.documents.constants.CompoundDocumentConstants.COMPOUND_PREVIEW_DETECTION_OPERATION;
import static org.nuxeo.runtime.model.Descriptor.UNIQUE_DESCRIPTOR_ID;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.compound.documents.configs.IgnoredFilesDescriptor;
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
            List<ZipEntry> entries = zip.stream().filter(this::isAllowedEntry).collect(Collectors.toList());
            addDirectories(entries);
            entries = entries.stream().sorted(Comparator.comparing(ZipEntry::getName)).collect(Collectors.toList());
            // Avoid giving the whole blobs
            List<String> entryNames = entries.stream().map(ZipEntry::getName).collect(Collectors.toList());
            String compoundType = runScript(COMPOUND_DOCTYPE_DETECTION_OPERATION, session, parentDoc,
                    Map.of("entries", entryNames));
            DocumentModel compoundDoc = session.createDocumentModel(parent, compoundDocName, compoundType);
            compoundDoc.setPropertyValue("dc:title", compoundDocName);
            final DocumentModel finalDoc = session.createDocument(compoundDoc);
            String compoundFolderType = runScript(COMPOUND_FOLDER_DOCTYPE_DETECTION_OPERATION, session, finalDoc,
                    Map.of());
            List<Map<String, Serializable>> compoundDocumentFileDef = new ArrayList<>();
            entries.forEach(entry -> createEntry(session, finalDoc, zip, entry, compoundFolderType, compoundDocumentFileDef));
            setCompoundDocumentFileDefinitionProp(finalDoc, compoundDocumentFileDef);
            final DocumentModel finalDocWithPreview = runScriptForPreview(COMPOUND_PREVIEW_DETECTION_OPERATION, session, finalDoc,
                    Map.of());
            finalDocWithPreview.putContextData(CoreSession.SOURCE, "compound");
            session.saveDocument(finalDocWithPreview);
            return finalDoc;
        } catch (IOException e) {
            var message = String.format("Failed to create compound document for archive: %s in parent: %s",
                    archive.getFilename(), parent);
            throw new NuxeoException(message, e);
        }
    }

    private List<ZipEntry> addDirectories(List<ZipEntry> entries) {
        List<ZipEntry> directories = new ArrayList<>();
        Set<String> content = new HashSet<>();
        entries.forEach(entry -> content.add(entry.getName()));
        for (ZipEntry file : entries) {
            String[] segments = new Path(file.getName()).segments();
            for(String segment : segments) {
                if (new Path(segment).getFileExtension() == null && content.add(segment + "/")) {
                    directories.add(new ZipEntry(segment + "/"));
                }
            }
        }
        entries.addAll(directories);
        return entries;
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

    protected DocumentModel runScriptForPreview(String scriptId, CoreSession session, DocumentModel doc, Map<String, ?> params) {
        AutomationService automationService = Framework.getService(AutomationService.class);
        try (OperationContext ctx = new OperationContext(session)) {
            ctx.setInput(doc);
            var result = (DocumentModel) automationService.run(ctx, scriptId, params);
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
            String compoundFolderType, List<Map<String, Serializable>> compoundDocumentFileDef) {
        Path entryPath = new Path(entry.getName());
        Path entryParentPath = entryPath.removeLastSegments(1);
        String parentDocPath = compoundDoc.getPath().append(entryParentPath).toString();
        String entryDocName = entryPath.lastSegment();
        if (entry.isDirectory()) {
            var doc = session.createDocumentModel(parentDocPath, entryDocName, compoundFolderType);
            doc.setPropertyValue("dc:title", entryDocName);
            doc.addFacet("CompoundDocumentFolder");
            session.createDocument(doc);
        } else {
            DocumentModel doc;
            var blob = new ZipEntryBlob(zip, entry);
            blob.setFilename(entryDocName);

            FileImporterContext ctx;
            List<String> supportedExtensions = new ArrayList<>(List.of("obj","glb"));
            if (supportedExtensions.contains(new Path(entryDocName).getFileExtension())) {
                ctx = FileImporterContext.builder(session, blob, parentDocPath).bypassAllowedSubtypeCheck(true).build();
            } else {
                ctx = FileImporterContext.builder(session, blob, parentDocPath).build();
            }
            try {
                var fileManager = Framework.getService(FileManager.class);
                doc = fileManager.createOrUpdateDocument(ctx);
                doc.putContextData(CoreSession.SOURCE, "compound-child");
                session.saveDocument(doc);
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
//            if (FilenameUtils.removeExtension(entry.getName()).equals("preview")) {
//                compoundDoc.setPropertyValue("cp:preview", doc.getId());
//            }

            Map<String, Serializable> entryFileDef = new HashMap<>();
            entryFileDef.put("latestVersionDocId", session.getLastDocumentVersion(doc.getRef()).getId());
            entryFileDef.put("latestVersion", doc.getPath().removeFirstSegments(compoundDoc.getPath().segmentCount() - 1).toString() + " - Version " + doc.getVersionLabel());
            entryFileDef.put("filepath", doc.getPathAsString());
            compoundDocumentFileDef.add(entryFileDef);


//            addCompoundDocumentFileDefinition(compoundDoc, session.getLastDocumentVersion(doc.getRef()).getId(), doc.getVersionLabel(), doc.getPathAsString(), doc.getPath().removeFirstSegments(compoundDoc.getPath().segmentCount() - 1).toString());
//            session.saveDocument(compoundDoc);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Serializable>> getCompoundDocumentFileDefinitionProp(DocumentModel compoundDoc) {
        return (List<Map<String, Serializable>>) compoundDoc.getPropertyValue("cp:files");
    }

//    public void addCompoundDocumentFileDefinitions(DocumentModel compoundDoc, String uid, String version, String filepath, String relativePath) {
//        List<Map<String, Serializable>> cpf = getCompoundDocumentFileDefinitionProp(compoundDoc);
//        if (cpf == null) {
//            cpf = new ArrayList<>();
//        }
//
//        Map<String, Serializable> file = new HashMap<>();
//        file.put("latestVersionDocId", uid);
//        file.put("latestVersion", relativePath + " - " + version);
//        file.put("filepath", filepath);
//
//        cpf.add(file);
//        setCompoundDocumentFileDefinitionProp(compoundDoc, cpf);
//    }

    public void setCompoundDocumentFileDefinitionProp(DocumentModel compoundDoc, List<Map<String, Serializable>> cpf) {
        compoundDoc.setPropertyValue("cp:files", (Serializable) cpf);
    }

    @Override
    public int getFileIndexBy(DocumentModel compoundDoc, String filepath) {
        List<Map<String, Serializable>> cpf = getCompoundDocumentFileDefinitionProp(compoundDoc);
        for (int index = 0; index < cpf.size(); index++) {
            Map<String, Serializable> fileProperties = cpf.get(index);
            if (filepath.equals(fileProperties.get("filepath"))) {
                return index;
            }
        }

        return -1;
    }

    @Override
    public void updateFileDefinition(DocumentModel compoundDoc, int index, Consumer<Map<String, Serializable>> consumer) {
        List<Map<String, Serializable>> cpf = getCompoundDocumentFileDefinitionProp(compoundDoc);

        consumer.accept(cpf.get(index));

        setCompoundDocumentFileDefinitionProp(compoundDoc, cpf);
    }



}
