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

import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.COMPOUND_DOCTYPE;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.COMPOUND_FOLDER_DOCTYPE;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.assertCompoundDocument;
import static org.nuxeo.compound.documents.CompoundDocumentUtils.getTestArchive;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.FilenameUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.core.operations.services.FileManagerImport;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.io.registry.MarshallingConstants;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.types.SubtypesJsonEnricher;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource.Builder;

@RunWith(FeaturesRunner.class)
@Features(CompoundDocumentsFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestCompoundDocumentRest extends BaseTest {

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void testCreateCompoundDocument() throws IOException {
        String data = "{\"entity-type\": \"document\",\"type\": \"" + COMPOUND_DOCTYPE
                + "\", \"name\": \"myCompoundDocument\"}";
        try (CloseableClientResponse response = getResponse(RequestType.POST, "path/", data)) {
            assertEquals(SC_CREATED, response.getStatus());
        }
        var headers = Map.of(MarshallingConstants.EMBED_ENRICHERS + ".document", SubtypesJsonEnricher.NAME);
        try (CloseableClientResponse response = getResponse(RequestType.GET, "path/myCompoundDocument", headers)) {
            assertCompoundResponse(response, Set.of(COMPOUND_DOCTYPE, "Folderish"));
        }
    }

    @Test
    public void testCreateCompoundDocumentFolder() throws IOException {
        session.createDocument(session.createDocumentModel("/", "myCompoundDocument", COMPOUND_DOCTYPE));
        txFeature.nextTransaction();

        String data = "{\"entity-type\": \"document\",\"type\": \"" + COMPOUND_FOLDER_DOCTYPE
                + "\", \"name\": \"myCompoundDocumentFolder\"}";
        try (CloseableClientResponse response = getResponse(RequestType.POST, "path/myCompoundDocument", data)) {
            assertEquals(SC_CREATED, response.getStatus());
        }

        var headers = Map.of(MarshallingConstants.EMBED_ENRICHERS + ".document", SubtypesJsonEnricher.NAME);
        try (CloseableClientResponse response = getResponse(RequestType.GET,
                "path/myCompoundDocument/myCompoundDocumentFolder", headers)) {
            assertCompoundResponse(response, Set.of("Folderish"));
        }
    }

    protected void assertCompoundResponse(CloseableClientResponse response, Set<String> expectedFacets)
            throws IOException {
        assertEquals(SC_OK, response.getStatus());
        JsonNode node = mapper.readTree(response.getEntityInputStream());
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

    protected DocumentModel testCompound(String target) throws IOException {
        String batchId;
        try (CloseableClientResponse response = getResponse(RequestType.POST, "upload")) {
            JsonNode node = mapper.readTree(response.getEntityInputStream());
            batchId = node.get("batchId").asText();
        }
        Blob blob = getTestArchive();
        var fileName = blob.getFilename();
        Builder builder = service.path("upload/" + batchId + "/0")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("X-File-Type", "application/zip")
                                 .header("X-File-Name", fileName);

        try (var in = blob.getStream();
                CloseableClientResponse response = CloseableClientResponse.of(builder.post(ClientResponse.class, in))) {
            assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
        }
        String data = String.format("{ \"context\": { \"currentDocument\": \"%s\" } }", target);
        try (CloseableClientResponse response = getResponse(RequestType.POST,
                "upload/" + batchId + "/execute/" + FileManagerImport.ID, data)) {
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
        }

        txFeature.nextTransaction();
        String docName = FilenameUtils.removeExtension(fileName);
        var compoundDocument = session.getDocument(new PathRef(target + docName));
        assertCompoundDocument(compoundDocument);
        return compoundDocument;
    }
}
