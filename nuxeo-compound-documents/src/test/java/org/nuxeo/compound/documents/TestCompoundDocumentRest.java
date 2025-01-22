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

import static jakarta.servlet.http.HttpServletResponse.SC_CREATED;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.COMPOUND_DOCTYPE;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.COMPOUND_FOLDER_DOCTYPE;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.assertCompoundDocument;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.getTestArchive;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.apache.commons.io.FilenameUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.core.operations.services.FileManagerImport;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.io.registry.MarshallingConstants;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.types.SubtypesJsonEnricher;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;

/** @since 2021.0 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, CompoundDocumentsFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestCompoundDocumentRest {

    @Inject
    public CoreSession session;

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(() -> restServerFeature.getRestApiUrl())
                                                                   .adminCredentials()
                                                                   .build();

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void testCreateCompoundDocument() {
        String payload = """
                {
                  "entity-type": "document",
                  "type": "CompoundDocument",
                  "name": "myCompoundDocument"
                }
                """;
        httpClient.buildPostRequest("path/")
                  .addHeader("Content-Type", MediaType.APPLICATION_JSON)
                  .entity(payload)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
        var headers = Map.of(MarshallingConstants.EMBED_ENRICHERS + ".document", SubtypesJsonEnricher.NAME);
        httpClient.buildGetRequest("/path/myCompoundDocument")
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(SC_OK),
                          node -> assertCompoundResponse(node, Set.of(COMPOUND_DOCTYPE, "Folderish")));
    }

    @Test
    public void testCreateCompoundDocumentFolder() {
        session.createDocument(session.createDocumentModel("/", "myCompoundDocument", COMPOUND_DOCTYPE));
        txFeature.nextTransaction();
        String payload = """
                {
                  "entity-type": "document",
                  "type": "CompoundDocumentFolder",
                  "name": "myCompoundDocumentFolder"
                }
                """;
        httpClient.buildPostRequest("/path/myCompoundDocument")
                  .addHeader("Content-Type", MediaType.APPLICATION_JSON)
                  .entity(payload)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        var headers = Map.of(MarshallingConstants.EMBED_ENRICHERS + ".document", SubtypesJsonEnricher.NAME);
        httpClient.buildGetRequest("/path/myCompoundDocument/myCompoundDocumentFolder")
                  .addHeaders(headers)
                  .executeAndConsume(new JsonNodeHandler(SC_OK),
                          node -> assertCompoundResponse(node, Set.of("Folderish")));
    }

    protected void assertCompoundResponse(JsonNode node, Set<String> expectedFacets) {
        JsonNode facets = node.get("facets");
        assertEquals(expectedFacets.size(), facets.size());
        facets.forEach(f -> assertTrue(expectedFacets.contains(f.asText())));
        var actualSubtypes = node.get("contextParameters").get("subtypes");
        assertEquals(5, actualSubtypes.size());
        var allowedSubtypes = Set.of("File", "Picture", "Video", "Audio", COMPOUND_FOLDER_DOCTYPE);
        actualSubtypes.forEach(t -> assertTrue(allowedSubtypes.contains(t.get("type").asText())));
    }

    @Test
    public void testCompoundDocumentUpload() throws IOException {
        testCompound("/");
    }

    @Test
    public void testNestingCompoundDocuments() throws IOException {
        DocumentModel doc = session.createDocumentModel("/", "test", COMPOUND_DOCTYPE);
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        testCompound(doc.getPathAsString() + "/");
    }

    protected void testCompound(String target) throws IOException {
        String batchId = httpClient.buildPostRequest("/upload")
                                   .executeAndThen(new JsonNodeHandler(SC_CREATED),
                                           node -> node.get("batchId").asText());
        Blob blob = getTestArchive();
        var fileName = blob.getFilename();
        httpClient.buildPostRequest("/upload/" + batchId + "/0")
                  .accept(MediaType.APPLICATION_JSON)
                  .entity(blob.getStream())
                  .addHeader("X-File-Type", "application/zip")
                  .addHeader("X-File-Name", fileName)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
        String data = String.format("{ \"context\": { \"currentDocument\": \"%s\" } }", target);
        httpClient.buildPostRequest("/upload/" + batchId + "/execute/" + FileManagerImport.ID)
                  .accept(MediaType.APPLICATION_JSON)
                  .entity(data)
                  .addHeader("Content-Type", MediaType.APPLICATION_JSON)
                  .addHeader("Accept-Encoding", "gzip")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        txFeature.nextTransaction();
        String docName = FilenameUtils.removeExtension(fileName);
        var compoundDocument = session.getDocument(new PathRef(target + docName));
        assertCompoundDocument(compoundDocument);
    }
}
