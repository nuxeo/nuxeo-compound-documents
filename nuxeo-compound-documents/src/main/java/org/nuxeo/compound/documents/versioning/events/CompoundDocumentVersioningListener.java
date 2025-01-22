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
 */
package org.nuxeo.compound.documents.versioning.events;

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDIN;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_RESTORED;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.compound.documents.adapters.CompoundDocument;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

public class CompoundDocumentVersioningListener implements PostCommitEventListener {

    public static final String STRICT_VERSION_RESTORE_ENABLED = "nuxeo.compound.document.strict.version.restore.enabled";

    private static final Logger log = LogManager.getLogger(CompoundDocumentVersioningListener.class);

    private static final String RESTORED_VERSION_UUID = "RESTORED_VERSION_UUID";

    private DocumentModel document;

    private CoreSession coreSession;

    public boolean acceptEvent(Event event) {
        return DOCUMENT_CHECKEDIN.equals(event.getName()) || DOCUMENT_RESTORED.equals(event.getName());
    }

    @Override
    public void handleEvent(EventBundle eventBundle) {
        for (Event event : eventBundle) {
            if (acceptEvent(event)) {
                EventContext ctx = event.getContext();
                if (ctx instanceof DocumentEventContext docCtx) {
                    document = docCtx.getSourceDocument();
                    coreSession = docCtx.getCoreSession();
                    if (DOCUMENT_CHECKEDIN.equals(event.getName()) && !document.getVersionLabel().equals("0.1")) {
                        List<DocumentModel> parents = coreSession.getParentDocuments(document.getRef());
                        Collections.reverse(parents);
                        parents.stream()
                               .filter(parent -> parent.hasFacet("CompoundDocument")
                                       && !parent.getId().equals(document.getId()))
                               .findFirst()
                               .ifPresent(this::handleCheckedInEvent);
                    } else if (DOCUMENT_RESTORED.equals(event.getName()) && document.hasFacet("CompoundDocument")) {
                        handleRestoredEvent(event);
                    }
                }
            }
        }
    }

    protected void handleRestoredEvent(Event event) {
        String removeDocument = Framework.getProperty(STRICT_VERSION_RESTORE_ENABLED, "false");
        DocumentRef restoringDocumentRef = new IdRef(event.getContext().getProperty(RESTORED_VERSION_UUID).toString());
        DocumentModel restoringDocument = coreSession.getDocument(restoringDocumentRef);
        DocumentModel latestDocument = coreSession.getLastDocumentVersion(document.getRef());
        List<String> diffChildren = latestDocument.getAdapter(CompoundDocument.class)
                                                  .findDiffChildren(
                                                          restoringDocument.getAdapter(CompoundDocument.class));

        for (String restoringChildId : diffChildren) {
            DocumentModel targetChildDocument = coreSession.getSourceDocument(new IdRef(restoringChildId));
            DocumentModel restoringChildDocument = coreSession.getDocument(new IdRef(restoringChildId));

            // restoring to the older version as per parent
            coreSession.restoreToVersion(targetChildDocument.getRef(), restoringChildDocument.getRef(), true, true);

            // removing all the version created after the restoring version
            if (Boolean.parseBoolean(removeDocument)) {
                coreSession.getVersionsForDocument(targetChildDocument.getRef())
                           .stream()
                           .filter(doc -> Double.parseDouble(doc.getLabel()) > Double.parseDouble(
                                   restoringChildDocument.getVersionLabel()))
                           .forEach(doc -> coreSession.removeDocument(new IdRef(doc.getId())));
            }
        }

    }

    protected void handleCheckedInEvent(DocumentModel compoundDocument) {
        compoundDocument.getAdapter(CompoundDocument.class).updateFileDefinition(coreSession, document);
    }

}
