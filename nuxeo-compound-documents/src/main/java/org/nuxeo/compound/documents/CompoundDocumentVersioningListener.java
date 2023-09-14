package org.nuxeo.compound.documents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.VersionModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDIN;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_RESTORED;

public class CompoundDocumentVersioningListener implements PostCommitEventListener{

    private static final Logger log = LogManager.getLogger(CompoundDocumentVersioningListener.class);
    private static final String RESTORED_VERSION_UUID = "RESTORED_VERSION_UUID";

    private DocumentModel document;

    private CoreSession coreSession;

    private Optional<DocumentModel> parentCompoundDoc;

    public boolean acceptEvent(Event event) {
        return DOCUMENT_CHECKEDIN.equals(event.getName()) || DOCUMENT_RESTORED.equals(event.getName());
    }

    @Override
    public void handleEvent(EventBundle eventBundle) {
        for (Event event : eventBundle) {
            if (acceptEvent(event)) {
                EventContext ctx = event.getContext();
                if (ctx instanceof DocumentEventContext) {
                    DocumentEventContext docCtx = (DocumentEventContext) ctx;
                    document = docCtx.getSourceDocument();
                    coreSession = docCtx.getCoreSession();
                    parentCompoundDoc = coreSession.getParentDocuments(document.getRef())
                            .stream()
                            .filter(parent -> parent.hasFacet("CompoundDocument"))
                            .filter(parent -> !parent.getId().equals(document.getId()))
                            .findFirst();
                }
                if (DOCUMENT_CHECKEDIN.equals(event.getName()) && parentCompoundDoc.isPresent()  && !document.getVersionLabel().equals("0.1")) {
                    handleCheckedInEvent();
                } else if (DOCUMENT_RESTORED.equals(event.getName()) && document.hasFacet("CompoundDocument")) {
                    handleRestoredEvent(event);
                }

            }
        }
    }

    protected void handleRestoredEvent(Event event) {
        DocumentModel restoringDocument = coreSession
                .getDocument(new IdRef(event.getContext().getProperty(RESTORED_VERSION_UUID).toString()));
        DocumentModel latestDocument = coreSession.getLastDocumentVersion(document.getRef());
        List<String> diffChildren = findDiffChildren(latestDocument, restoringDocument);

        for (String restoringChildId : diffChildren) {
            DocumentModel targetChildDocument = coreSession.getSourceDocument(new IdRef(restoringChildId));
            DocumentModel restoringChildDocument = coreSession.getDocument(new IdRef(restoringChildId));

            //restoring to the older version as per parent
            coreSession.restoreToVersion(
                    targetChildDocument.getRef(),
                    restoringChildDocument.getRef(),
                    true,
                    true
            );

            // removing all the version created after the restoring version
            coreSession.getVersionsForDocument(targetChildDocument.getRef())
                    .stream()
                    .filter(doc -> Double.parseDouble(doc.getLabel()) > Double.parseDouble(restoringChildDocument.getVersionLabel()))
                    .forEach(doc -> coreSession.removeDocument(new IdRef(doc.getId())));
        }

    }

    private List<String> findDiffChildren(DocumentModel document, DocumentModel restoringDocument) {

        List<Map<String, Serializable>> documentChildren = (List<Map<String, Serializable>>) document.getPropertyValue("cp:files");
        List<Map<String, Serializable>> restoringDocumentChildren = (List<Map<String, Serializable>>) restoringDocument.getPropertyValue("cp:files");
        List<String> diffChildren = new ArrayList<>();

        for (int i = 0; i < documentChildren.size(); i++) {
            if (!documentChildren.get(i).get("latestVersionDocId").equals(restoringDocumentChildren.get(i).get("latestVersionDocId"))) {
                diffChildren.add(restoringDocumentChildren.get(i).get("latestVersionDocId").toString());
            }
        }
        return diffChildren;

    }

    protected void handleCheckedInEvent() {
        DocumentModel parent;
        if (parentCompoundDoc.isPresent()) {
            parent = parentCompoundDoc.get();
        } else {
            return;
        }
        Optional<VersionModel> latestVersion = coreSession.
                getVersionsForDocument(document.getRef()).stream().max(Comparator.comparing(VersionModel::getCreated));

        CompoundDocumentService compoundDocumentService = Framework.getService(CompoundDocumentService.class);
        compoundDocumentService.updateFileDefinition(parent, compoundDocumentService.getFileIndexBy(parent, document.getPathAsString()), file -> {
            file.put("latestVersionDocId", latestVersion.get().getId());
            file.put("latestVersion", document.getPath().removeFirstSegments(parent.getPath().segmentCount() - 1).toString() + " - " + latestVersion.get().getLabel());
            file.put("filepath", document.getPathAsString());
        });
        coreSession.saveDocument(parent);
    }

}
