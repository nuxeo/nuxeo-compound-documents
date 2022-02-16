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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.video:OSGI-INF/core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.audio.core:OSGI-INF/core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.tag:OSGI-INF/faceted-tag-service-core-types.xml")
@Deploy("org.nuxeo.ecm.platform.picture.core:OSGI-INF/picture-schemas-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.types:OSGI-INF/subtypes-enricher-contrib.xml")
@Deploy("org.nuxeo.compound.documents")
public class TestCompoundDocument extends AbstractJsonWriterTest.Local<DocumentModelJsonWriter, DocumentModel> {

    @Inject
    protected CoreSession session;
    
    public TestCompoundDocument() {
        super(DocumentModelJsonWriter.class, DocumentModel.class);
    }

    @Test
    public void testCompoundDocument() throws IOException {
        DocumentModel doc = session.createDocument(session.createDocumentModel("/", "test", "CompoundDocument"));
        assertTrue(doc.getFacets().contains("CompoundDocument"));
        RenderingContext ctx = RenderingContext.CtxBuilder.enrichDoc("subtypes").get();
        JsonAssert json = jsonAssert(doc, ctx);
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has("subtypes").isArray();
        json.childrenContains("type", "File", "Picture", "Video", "Audio", "CompoundDocumentFolder");

        doc = session.createDocumentModel("/", "test", "CompoundDocumentFolder");
        assertFalse(doc.getFacets().contains("CompoundDocument"));
        assertTrue(doc.getFacets().contains("Folderish"));
        ctx = RenderingContext.CtxBuilder.enrichDoc("subtypes").get();
        json = jsonAssert(doc, ctx);
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has("subtypes").isArray();
        json.childrenContains("type", "File", "Picture", "Video", "Audio", "CompoundDocumentFolder");
    }
}
