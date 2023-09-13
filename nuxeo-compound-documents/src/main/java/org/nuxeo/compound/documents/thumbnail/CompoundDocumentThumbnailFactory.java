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
package org.nuxeo.compound.documents.thumbnail;

import static org.nuxeo.ecm.platform.thumbnail.ThumbnailConstants.THUMBNAIL_FACET;
import static org.nuxeo.ecm.platform.thumbnail.ThumbnailConstants.THUMBNAIL_PROPERTY_NAME;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.thumbnail.ThumbnailService;
import org.nuxeo.ecm.platform.thumbnail.factories.ThumbnailDocumentFactory;
import org.nuxeo.runtime.api.Framework;

public class CompoundDocumentThumbnailFactory extends ThumbnailDocumentFactory {

    @Override
    public Blob getThumbnail(DocumentModel doc, CoreSession session) {

        // give priority to thumbnail property
        if (doc.hasFacet(THUMBNAIL_FACET)) {
            var blob = (Blob) doc.getPropertyValue(THUMBNAIL_PROPERTY_NAME);
            if (blob != null) {
                return blob;
            }
        }

        // then from the preview document
        var previewDocId = (String) doc.getPropertyValue("cp:preview");
        var thumbnailBlob = getThumbnailFromDocId(previewDocId, session);
        if (thumbnailBlob != null) {
            return thumbnailBlob;
        }

        // fallback to default factory
        return getDefaultThumbnail(doc);
    }

    public Blob getThumbnailFromDocId(String docId, CoreSession session) {
        if (StringUtils.isNotEmpty(docId)) {
            var thumbnailService = Framework.getService(ThumbnailService.class);
            var doc = session.getDocument(new IdRef(docId));
            return thumbnailService.getThumbnail(doc, session);
        }
        return null;
    }

}
