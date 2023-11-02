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
package org.nuxeo.compound.documents.marshallers;

import static org.nuxeo.compound.documents.constants.CompoundDocumentConstants.COMPOUND_DOCUMENT_FACET;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelListJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext.SessionWrapper;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Enrich {@link DocumentModel} Json.
 * <p>
 * Add compound document breadcrumb (list of all compound document ancestors) as json attachment.
 * <p>
 * Enable if parameter enrichers-document=compoundDocumentBreadcrumb is present.
 * <p>
 * Format is:
 *
 * <pre>
 * {
 *   "entity-type":"document",
 *   ...
 *   "contextParameters": {
 *     "compoundDocumentBreadcrumb": { see {@link DocumentModelListJsonWriter} for format }
 *   }
 * }
 * </pre>
 *
 * @since 2021.0
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class CompoundDocumentBreadcrumbJsonEnricher extends AbstractJsonEnricher<DocumentModel> {

    public static final String NAME = "compoundDocumentBreadcrumb";

    public CompoundDocumentBreadcrumbJsonEnricher() {
        super(NAME);
    }

    @Override
    public void write(JsonGenerator jg, DocumentModel document) throws IOException {
        try (SessionWrapper wrapper = ctx.getSession(document)) {
            if (!wrapper.getSession().exists(document.getRef())) {
                return;
            }
            var parentCompounds = wrapper.getSession()
                                         .getParentDocuments(document.getRef())
                                         .stream()
                                         .filter(d -> d.hasFacet(COMPOUND_DOCUMENT_FACET))
                                         .collect(Collectors.toCollection(DocumentModelListImpl::new));
            jg.writeFieldName(NAME);
            writeEntity(parentCompounds, jg);
        }
    }

}
