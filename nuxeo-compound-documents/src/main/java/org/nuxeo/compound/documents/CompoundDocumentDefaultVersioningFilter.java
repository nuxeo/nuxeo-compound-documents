package org.nuxeo.compound.documents;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.versioning.VersioningPolicyFilter;

public class CompoundDocumentDefaultVersioningFilter implements VersioningPolicyFilter {

    @Override
    public boolean test(DocumentModel previousDocument, DocumentModel currentDocument) {
        return currentDocument.getContextData().get(CoreSession.SOURCE) == "compound";
    }

}
