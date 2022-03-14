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
package org.nuxeo.compound.documents.rest.tests;

import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.SubtypesJsonEnricher;
import org.nuxeo.ecm.core.io.registry.MarshallingConstants;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

@RunWith(FeaturesRunner.class)
@Features(RestServerFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.video:OSGI-INF/core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.audio.core:OSGI-INF/core-types-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.picture.core:OSGI-INF/picture-schemas-contrib.xml")
@Deploy("org.nuxeo.compound.documents")
public class TestCompoundDocument extends BaseTest {

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void testCreateCompoundDocument() throws IOException {
        String data = "{\"entity-type\": \"document\",\"type\": \"CompoundDocument\", \"name\": \"myCompoundDocument\"}";
        try (CloseableClientResponse response = getResponse(RequestType.POST, "path/", data)) {
            assertEquals(SC_CREATED, response.getStatus());
        }
        var headers = Map.of(MarshallingConstants.EMBED_ENRICHERS + ".document", SubtypesJsonEnricher.NAME);
        try (CloseableClientResponse response = getResponse(RequestType.GET, "path/myCompoundDocument", headers)) {
            assertCompoundResponse(response, "CompoundDocument");
        }
    }

    @Test
    public void testCreateCompoundDocumentFolder() throws IOException {
        session.createDocument(session.createDocumentModel("/", "myCompoundDocument", "CompoundDocument"));
        txFeature.nextTransaction();
        String data = "{\"entity-type\": \"document\",\"type\": \"CompoundDocumentFolder\", \"name\": \"myCompoundDocumentFolder\"}";
        try (CloseableClientResponse response = getResponse(RequestType.POST, "path/myCompoundDocument", data)) {
            assertEquals(SC_CREATED, response.getStatus());
        }
        var headers = Map.of(MarshallingConstants.EMBED_ENRICHERS + ".document", SubtypesJsonEnricher.NAME);
        try (CloseableClientResponse response = getResponse(RequestType.GET,
                "path/myCompoundDocument/myCompoundDocumentFolder", headers)) {
            assertCompoundResponse(response, "Folderish");
        }
    }

    protected void assertCompoundResponse(CloseableClientResponse response, String expectedFacet) throws IOException {
        assertEquals(SC_OK, response.getStatus());
        JsonNode node = mapper.readTree(response.getEntityInputStream());
        assertEquals(1, node.get("facets").size());
        assertEquals(expectedFacet, node.get("facets").get(0).asText());
        var actualSubtypes = node.get("contextParameters").get("subtypes");
        assertEquals(5, actualSubtypes.size());
        var allowedSubtypes = Set.of("File", "Picture", "Video", "Audio", "CompoundDocumentFolder");
        actualSubtypes.forEach(t -> assertTrue(allowedSubtypes.contains(t.get("type").asText())));
    }
}
