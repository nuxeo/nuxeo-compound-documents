/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Guillaume Renard
 */
package org.nuxeo.compound.documents.adapters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.VersionModel;

/**
 * @since 2025.0
 */
public class CompoundDocument {

    protected DocumentModel document;

    public CompoundDocument(final DocumentModel doc) {
        document = doc;
    }

    public DocumentModel getDocument() {
        return document;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Serializable>> getCompoundDocumentFileDefinitionProp() {
        return (List<Map<String, Serializable>>) document.getPropertyValue("cp:files");
    }

    public void setCompoundDocumentFileDefinitionProp(List<Map<String, Serializable>> cpf) {
        document.setPropertyValue("cp:files", (Serializable) cpf);
    }

    public Optional<Map<String, Serializable>> getFileByPath(String filepath) {
        Objects.requireNonNull(filepath);
        return getCompoundDocumentFileDefinitionProp().stream()
                                                      .filter(fileProperties -> filepath.equals(
                                                              fileProperties.get("filepath")))
                                                      .findFirst();
    }

    public List<String> findDiffChildren(CompoundDocument restoringDocument) {
        List<Map<String, Serializable>> documentChildren = getCompoundDocumentFileDefinitionProp();
        List<Map<String, Serializable>> restoringDocumentChildren = restoringDocument.getCompoundDocumentFileDefinitionProp();
        List<String> diffChildren = new ArrayList<>();

        for (int i = 0; i < documentChildren.size(); i++) {
            if (!documentChildren.get(i)
                    .get("latestVersionDocId")
                    .equals(restoringDocumentChildren.get(i).get("latestVersionDocId"))) {
                diffChildren.add(restoringDocumentChildren.get(i).get("latestVersionDocId").toString());
            }
        }
        return diffChildren;
    }

    public void updateFileDefinition(CoreSession session, DocumentModel child) {
        session.getVersionsForDocument(child.getRef())
               .stream()
               .max(Comparator.comparing(VersionModel::getCreated))
               .ifPresent(latestVersion -> {
                   getFileByPath(child.getPathAsString()).ifPresent(file ->{
                       file.put("latestVersionDocId", latestVersion.getId());
                       file.put("latestVersion",
                               child.getPath().removeFirstSegments(document.getPath().segmentCount() - 1).toString()
                                       + " - Version " + latestVersion.getLabel());
                       file.put("filepath", child.getPathAsString());
                       session.saveDocument(document);
                   });

               });
    }
}
