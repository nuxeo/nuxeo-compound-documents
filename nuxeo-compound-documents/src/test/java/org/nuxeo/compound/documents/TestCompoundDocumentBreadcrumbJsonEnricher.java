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

import static org.nuxeo.compound.documents.CompoundDocumentBreadcrumbJsonEnricher.NAME;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.COMPOUND_DOCTYPE;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.COMPOUND_FOLDER_DOCTYPE;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext.CtxBuilder;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/** @since 2021.0 */
@RunWith(FeaturesRunner.class)
@Features(CompoundDocumentsFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestCompoundDocumentBreadcrumbJsonEnricher
        extends AbstractJsonWriterTest<DocumentModelJsonWriter, DocumentModel> {

    @Inject
    protected CoreSession session;

    public TestCompoundDocumentBreadcrumbJsonEnricher() {
        super(DocumentModelJsonWriter.class, DocumentModel.class);
    }

    @Test
    public void test() throws IOException {
        var level1Doc = session.createDocumentModel("/", "level1", COMPOUND_DOCTYPE);
        session.createDocument(level1Doc);
        var level2Doc = session.createDocumentModel("/level1/", "level2", COMPOUND_FOLDER_DOCTYPE);
        session.createDocument(level2Doc);
        var level3Doc = session.createDocumentModel("/level1/level2", "level3", COMPOUND_DOCTYPE);
        session.createDocument(level3Doc);
        var level4Doc = session.createDocumentModel("/level1/level2/level3", "level4", "File");
        level4Doc = session.createDocument(level4Doc);

        JsonAssert json = jsonAssert(level4Doc, CtxBuilder.enrichDoc(NAME).get());
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has(NAME).isObject();
        json.has("entity-type").isEquals("documents");
        json = json.has("entries").length(2);
        JsonAssert doc = json.has(0);
        doc.has("entity-type").isEquals("document");
        doc.has("title").isEquals("level1");
        doc = json.has(1);
        doc.has("entity-type").isEquals("document");
        doc.has("title").isEquals("level3");
    }
}
